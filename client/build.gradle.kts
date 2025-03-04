// Import Docker tasks from the bmuschko plugin for image building and container management
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

// Applying necessary plugins for Java application dev. and Docker support
plugins {
    id("java")                               // Enables Java support for this Gradle module
    id("application")                        // Defines this project as an application with a main entry point
    id("com.bmuschko.docker-remote-api")     // Adds Docker support via the bmuschko Gradle plugin. This automates Docker builds.
}

/*
    Declaring Dependencies for the client module only - Dependencies should be declared before tasks.
    This ensures all modules know their dependencies before any Gradle tasks run.
*/

dependencies {
    implementation(project(":utilities"))                                   // Adds dependency on the utilities module
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")    // JSON support
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")            // JUnit 5 testing framework
}


// Configure the application plugin with the main class for launching the app - Client.java in this case
application {
    mainClass.set("io.github.cpsc559.team16.client.ClientTest") // Inform Gradle which class contains the main method for launching the app.
}

// Configure the default jar task to build the client JAR file
tasks.jar {
    // Set the output file name for the JAR
    archiveFileName.set("client.jar")

    // Set the directory where the JAR file will be placed
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    // Configure the manifest file of the JAR
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

    // Include the compiled classes of this module (client)
    from(sourceSets.main.get().output)

    // *** Explicitly include the compiled classes from the utilities module ***
    from(project(":utilities").sourceSets.main.get().output)

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
    dockerFile.set(file("Dockerfile"))              // Uses the `Dockerfile` located in this directory for building the image
    images.add("client:latest")                         // Tags the built Docker image as "client:latest"
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

    doLast {
        println("Docker image client:latest removed.")
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
fun String.runCommand(): String {
    return ProcessBuilder(*this.split(" ").toTypedArray())
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText()
}

/*
    Task to safely remove an existing Docker container named "client_container" if it exists.
    This prevents conflicts by ensuring a clean state before creating a new container.
*/
//tasks.register("safeRemoveClientContainer") {
//    group = "docker-client"
//    description = "Removes the client container (client_container) if it exists, otherwise does nothing."
//
//    doLast {
//        // Execute a Docker command to list containers matching the name "client_container"
//        val result = "docker ps -a --filter \"name=^/client_container\$\" --format \"{{.ID}}\"".runCommand()
//        if (result.trim().isNotEmpty()) {
//            dependsOn("killClientContainer")
////            println("Container 'client_container' exists. Removing it...")
////            // Force removal of the container, even if it is running
////            val removeResult = "docker rm -f client_container".runCommand()
////            println("Removal output: $removeResult")
//        } else {
//            println("No container named 'client_container' found; nothing to remove.")
//        }
//    }
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
    description = "Creates a Docker container using the latest client image (client:latest)"

    dependsOn("safeRemoveClientContainer")   // Removes the client_container from the file system (if it exists)
    imageId.set("client:latest")            // Uses the image built from the Dockerfile
    containerName.set("client_container")   // Default docker behavior assigns a random container name

    // ---------- WE CAN DEFINE PORT BINDING HERE ----------------
    // hostConfig.portBindings.set(listOf("8080:8080")) // Port binding (if needed)

    // Printing the container ID to console. Can be removed if desired.
    doLast {
        println("Client Container Built - Name: ${containerName.get()}")
    }
}

// Ensures that when both tasks are scheduled, buildClientImage always completes before buildClientContainer starts.
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
    Task to build a new Client Container from the last Client Docker Image and start it.
    Default behavior of this task is to delete any previous client containers.
*/
tasks.register<DockerStartContainer>("runClientRetainImg") {
    group = "docker-client"
    description = "Builds a new container from the <client:latest> Image and starts it."

    // Only execute if the image <client:latest> exists. If it doesn't, log an error and skip this task.
    onlyIf {
        try {
            // Try to inspect the image. If it exists, exec() returns valid info.
            val imageInfo = dockerClient.inspectImageCmd("client:latest").exec()
            imageInfo != null
        } catch (e: Exception) {
            println("Image 'client:latest' does not exist: ${e.message}")
            false
        }
    }
    // This wrapper task depends on starting the container and then telling gradle to stream its logs.
    dependsOn("startNewClientContainer", "streamClientLogs")
    doLast {
        println("Client container started with image 'client:latest'.")
    }
}

/*
    Task to start a Client Container by first building a new Image from the Client Dockerfile.
    Default behavior of this task is to delete the client:latest image (if it exists) as well as any client containers.
*/
tasks.register("runClientWipeImg") {
    mustRunAfter("safeRemoveClientContainer","safeRemoveClientImage")
    group = "docker-client"
    description = "Builds a new container from scratch (Dockerfile->Client Image->Client Container)"
    // Images cannot be removed until the container relying on them is stopped.
    dependsOn("safeRemoveClientContainer","safeRemoveClientImage")
    dependsOn("buildClientImage", "startNewClientContainer","streamClientLogs")
}