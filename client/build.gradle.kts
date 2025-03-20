// Import Docker tasks from the bmuschko plugin for image building and container management
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.TaskAction
import java.util.*


// Applying necessary plugins for Java application dev. and Docker support
plugins {
    id("java")                               // Enables Java support for this Gradle module
    id("application")                        // Defines this project as an application with a main entry point
    id("com.bmuschko.docker-remote-api")     // Adds Docker support via the bmuschko Gradle plugin. This automates Docker builds.
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}
/*
    Declaring Dependencies for the client module only - Dependencies should be declared before tasks.
    This ensures all modules know their dependencies before any Gradle tasks run.
*/

dependencies {
    implementation(project(":common"))                                   // Adds dependency on the utilities module
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")    // JSON support
    implementation("org.jline:jline:3.21.0")                                // JLine for console input/output
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")            // JUnit 5 testing framework
}


// Configure the application plugin with the main class for launching the app - Client.java in this case
application {
    mainClass.set("io.github.cpsc559.team16.client.ClientTest") // Inform Gradle which class contains the main method for launching the app.
}

// Loading the port bindings from the client .env file
val envProperties = Properties().apply {
    file(".env").inputStream().use { load(it) }
}


// Configure the default jar task to build the client JAR file
tasks.jar {

    // Set the output file name for the JAR
    archiveFileName.set("client.jar")

    // Set the directory where the JAR file will be placed
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    /*
     Configure the manifest file of the JAR.
     This is how we set the "Main-Class" in the "Manifest" which tells the JVM which class contains the main...
     ...method that we want the client.jar (fat jar containing all Client module classes) to call when executed.
     */
    manifest {
        // Add the Main-Class attribute to the manifest.
        // This tells the JVM which class contains the main method to run.
        attributes("Main-Class" to "io.github.cpsc559.team16.client.ClientTest")
    }
}

// Task to create a fat (uber) JAR that packages the application along with all its dependencies
tasks.register<Jar>("clientFatJar") {
    group = "build"
    description = "Creates a runnable JAR for the Client application."

    // Set the output file name to "client.jar"
    archiveFileName.set("client.jar")

    // Place the JAR in the build/libs directory
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    // Include the compiled classes of this module (client).
    // Gradle compiles all the classes in the directory client/src/main/java automatically.
    from(sourceSets.main.get().output)

    // *** Explicitly include the compiled classes from the common module (Classes shared by several modules) ***
    from(project(":common").sourceSets.main.get().output)

    // Include all runtime dependencies by unpacking their JARs
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")

    // Include the manifest with the Main-Class attribute
    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.client.ClientTest")
    }
}

tasks.register("buildClient") {
    group = "build"
    description = "Builds the client3 server with common library dependencies."
    dependsOn(":common:commonJar", "clientFatJar")
}
/*
    Task to build and package the client JAR before the Docker image is created.
    It ensures that the fat JAR is compiled and available.
*/
tasks.register("buildClientJar") {
    group = "build"
    description = "Compiles and packages the client.jar before the Docker image is built."

    /*
    Gradle tasks are meant to be executed as distinct, independent units.
    When you declare a dependency (dependsOn) in Gradle, the dependent task must
    finish before the task that depends on it (the calling code) begins.
     */
    dependsOn("clientFatJar")

    doLast {
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        println("Client JAR successfully created: ${libsDir}/client.jar")
    }
}

// Task to build the Docker image for the client application using the bmuschko Docker plugin.
tasks.register<DockerBuildImage>("buildClientImage") {
    group = "docker-client"                          // Categorizes this task under the "docker" group when running `gradlew tasks` to see available tasks
    description = "Builds the Docker image for the client application."

    dependsOn("buildClientJar")                // Ensures the JAR is built before Docker runs (compiling everything in the container isn't ideal, especially if we want to spin up many processes)

    inputDir.set(file("."))			            // Sets the build context to the current directory (client module)
    dockerFile.set(file("Dockerfile"))          // Uses the `Dockerfile` located in this directory for building the image
    images.add("client:latest")                 // Tags the built Docker image as "client:latest"
    
    doLast{
        try{
            // Print the old Image ID
            val imageInfo = dockerClient.inspectImageCmd("client:latest").exec()
            println("Previous client Image ID: ${imageInfo.id}\n")    
        } catch (e:Exception){
            println("No previous image found for 'client:latest'. A new image will be created")
        }
    }
    
    
}

/*
 Task to safely remove an existing Docker container named "client_container" if it exists.
 This prevents conflicts by ensuring a clean state before creating a new container.
 */
tasks.register<DockerRemoveImage>("safeRemoveClientImage") {
    group = "docker-client"
    description = "Removes the Docker image <client:latest> if it exists."

    // Set the target image ID (all image builds are named client:latest by default)
    targetImageId("client:latest")
    force.set(true)

    // Only execute if the image exists. If it doesn't, log an error and skip this task.
    onlyIf {
        try {
            // Try to inspect the image. If it exists, exec() returns valid info.
            val imageInfo = dockerClient.inspectImageCmd("client:latest").exec()
            imageInfo != null
        } catch (e: Exception) {
            logger.error("Image 'client:latest' does not exist: ${e.message}")
            false
        }
    }
    doLast{
        println("Successfully removed previous client:latest image.")
    }
}


// Task to remove client_container if it is running.
tasks.register<DockerKillContainer>("killClientContainer") {
    group = "docker-client"
    description = "Kills the client container (client_container) if it exists."

    targetContainerId("client_container") // Tells gradle which container to target (the name is static in our implementation)

    // The onlyIf closure must return a Boolean value.
    // The return value determines whether the registered task will run (true) or be skipped (false).
    onlyIf {
        // In Kotlin - the last expression in the lambda is used as the return value
        try {
            // Inspect the container to check its state.
            // The returned response includes a 'state' with a 'running' Boolean property.
            val containerInfo = dockerClient.inspectContainerCmd("client_container").exec()
            containerInfo.state.running
        } catch (_: Exception) {
            false
        }
    }
    doLast {
        println("Container 'client_container' has been killed.")
    }
}

// Task to remove client_container if it exists and is not running.
tasks.register<DockerRemoveContainer>("removeClientContainer") {
    group = "docker-client"
    description = "Removes the client container if it is not running."

    targetContainerId("client_container") //
    force.set(true)

    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("client_container").exec()
            // Only remove if the container is not running.
            !containerInfo.state.running
        } catch (_: Exception) {
            // If the container does not exist at all, skip removal.
            false
        }
    }
    doLast {
        println("Container 'client_container' has been removed.")
    }
}

/*
    Part of a task chain that builds the Docker image from the client module's Dockerfile and runs the corresponding container.
    Composite task: When executed, it will depend on both tasks, but only one of them will actually run because of their onlyIf conditions.
*/
tasks.register("safeRemoveClientContainer") {
    group = "docker-client"
    description = "Kills the client container if running; otherwise removes it if it exists."
    dependsOn("killClientContainer", "removeClientContainer")
}

/*
 Helper function to execute shell commands and return their output as a String.
 This is used to interact with the Docker CLI from within Gradle.
 */
//fun String.runCommand(): String {
//    return ProcessBuilder(*this.split(" ").toTypedArray())
//            .redirectErrorStream(true)
//            .start()
//            .inputStream.bufferedReader().readText()
//}

/*
   We will need to create multiple containers if we want to run multiple processes.
   Default behaviour for this task is to delete the previous container.
     - If we go this route, we will need to create a separate task for each container - or automate it another way. Haven't looked into that yet!
     val clientContainer is a TaskProvider<DockerCreateContainer>
     Lazy registration (tasks.register) is more efficient than eager creation (tasks.create)
 */
val clientContainer = tasks.register<DockerCreateContainer>("buildClientContainer") {
    group = "docker-client"
    description = "Creates a Docker container using a new client image (client:latest)"
    dependsOn("safeRemoveClientContainer", "buildClientImage")   // Removes the client_container from the file system (if it exists)
    imageId.set("client:latest")                    // Uses the image built from the Dockerfile
    containerName.set("client_container")           // Default docker behavior assigns a random container name

    // ---------- WE CAN DEFINE PORT BINDING HERE ----------------
    // >---------------- WE CAN DEFINE PORT BINDING AND NETWORKS HERE ---------------------<
    //hostConfig.network.set("my-macvlan-network")
    hostConfig.portBindings.set(
            listOf(
                    "${envProperties.getProperty("CLIENT_ADDRSERVER_PORT")}:${envProperties.getProperty("CLIENT_ADDRSERVER_PORT")}",
                    "${envProperties.getProperty("CLIENT_CHATSERVER_PORT")}:${envProperties.getProperty("CLIENT_CHATSERVER_PORT")}",
            )
    )
    println("CLIENT_ADDRSERVER_PORT=${envProperties.getProperty("CLIENT_ADDRSERVER_PORT")}")
    println("CLIENT_CHATSERVER_PORT=${envProperties.getProperty("CLIENT_CHATSERVER_PORT")}")
    // Printing the container name and image ID to console
    doLast {
        println("Client Container Built - Name: ${containerName.get()}")
    }
}

// Ensures that when both tasks are scheduled, buildClientImage always completes before buildNewClientContainer starts.
tasks.named("buildClientContainer") {
    mustRunAfter("buildClientImage")
}

/*
    Task that allows us to see the console output of the client JAR.
    Must have already created the client_container Docker container before using this task.
*/
tasks.register<DockerLogsContainer>("streamClientLogs") {
    group = "docker-client"
    description = "Streams logs from the client container to the console."

    // Set the target container ID or name.
    targetContainerId("client_container")

    // Follow the logs continuously.
    follow.set(true)
}


/*
    Task to start the client container.
    It depends on the container being created, and starts the container so the application can run.
*/
tasks.register<DockerStartContainer>("startNewClientContainer") {
    group = "docker-client"
    description = "Builds a new container <client_container> and starts it."
    dependsOn(clientContainer) // Ensure the container is created first
    // Since we will only need one client, it is unlikely we need multiple containers per machine.
    targetContainerId("client_container")
}



/*
    Task to delete the current client container and build a new one while retaining the client Image on disk.
    If there have been any changes to classes in the module the container will be built from a NEW image.
*/
tasks.register("runClientRetainImg") {
    group = "docker-client"
    description = "Builds and runs a new Client Container from the current image - deletes the current Container.\n" +
            "\t\t\t(Dockerfile->Client Image->Client Container)"
    // This wrapper task depends on starting the container and then telling gradle to stream its logs.
    dependsOn("safeRemoveClientContainer", "startNewClientContainer", "streamClientLogs")
    doLast {
        println("Client container started from image 'client:latest'. Previous image remains on disk.")
    }
}


/*
This composite task builds a new container from scratch (Dockerfile -> client:latest Image -> client_container Container).
This task always builds a new client Docker Image and removies the old one from disk.
*/
tasks.register("runClientWipeImg") {
    group = "docker-client"
    description = "Builds and runs a new Client Container from a new Image - deletes the current Image and Container in the local directory.\n" +
            "\t\t\t(Dockerfile->Client Image->Client Container)"
    // Images cannot be removed until the container relying on them is stopped.
    dependsOn("safeRemoveClientContainer","safeRemoveClientImage")
    dependsOn("startNewClientContainer","streamClientLogs")
    doLast {
        println("Client container started from new image 'client:latest'. Previous image removed from disk.")
    }
}


// >-------------------- TASKS FOR OPENING NEW TERMINAL WHEN RUNNING A NEW CONTAINER ------------------<
tasks.register("runClientWindows") {
    group = "docker-client"
    description = "Builds and runs the Client container in a new Windows CMD terminal (interactive)."

    // Wipe old container & image, then ensure plugin-based container starts
    dependsOn("safeRemoveClientContainer", "safeRemoveClientImage")
    dependsOn("startNewClientContainer") // The Gradle DockerStartContainer that starts 'client_container'

    doLast {
        println("Client container started from new image 'client:latest'. Previous image removed from disk.")
        println("\nOpening a new Windows CMD window and attaching to client_container...")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")
        // Attach to the running container in a new terminal
        val attachCommand = "docker attach client_container"

        project.exec {
            commandLine("cmd", "/c", "start", "cmd", "/k", attachCommand)
        }
    }
}

tasks.register("runClientMacOS") {
    group = "docker-client"
    description = "Builds and runs the Client container in a new macOS Terminal (interactive)."

    dependsOn("safeRemoveClientContainer", "safeRemoveClientImage")
    dependsOn("startNewClientContainer")

    doLast {
        println("Client container started from new image 'client:latest'. Previous image removed from disk.")
        println("\nOpening a new macOS Terminal window and attaching to client_container...")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")
        val attachCommand = "docker attach client_container"

        // Use osascript to open a new macOS Terminal session
        project.exec {
            commandLine("osascript", "-e", "tell application \"Terminal\" to do script \"$attachCommand\"")
        }
    }
}

tasks.register("runClientLinux") {
    group = "docker-client"
    description = "Builds and runs the Client container in a new Linux terminal (interactive)."

    dependsOn("safeRemoveClientContainer", "safeRemoveClientImage")
    dependsOn("startNewClientContainer")

    doLast {
        println("Client container started from new image 'client:latest'. Previous image removed from disk.")
        println("\nOpening a new Linux terminal and attaching to client_container...")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")
        val attachCommand = "docker attach client_container"

        // For GNOME Terminal:
        project.exec {
            commandLine("gnome-terminal", "--", "bash", "-c", attachCommand)
        }

        // If you’re on KDE/XFCE/etc., uncomment this:
        // project.exec {
        //     commandLine("x-terminal-emulator", "-e", attachCommand)
        // }
    }
}
// >-------------------- END OF TASKS FOR OPENING NEW TERMINAL WHEN RUNNING A NEW CONTAINER ------------------<