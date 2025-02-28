// Import the DockerBuildImage, DockerCreateContainer tasks from the bmuschko plugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer

// Applying all the necessary plugins for Java application and Docker support
plugins {
    id("java")                               // Enables Java support for this Gradle module
    id("application")                        // Defines this project as an application with a main entry point
    id("com.bmuschko.docker-remote-api")     // Adds Docker support via the bmuschko Gradle plugin. This automates Docker builds.
}

// Declaring Dependencies for the client module only - Dependencies should be declared before tasks.
// This ensures all modules know their dependencies before any Gradle tasks run.
dependencies {
    implementation(project(":utilities"))                                   // Adds dependency on the utilities module
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")    // JSON support
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")            // JUnit 5 testing framework
}


// Configures the Java application plugin
application {
    mainClass.set("io.github.cpsc559.team16.client.Client") // Inform Gradle which class contains the main method for launching the app.
}

tasks.jar {
    // Set the output file name for the JAR
    archiveFileName.set("client.jar")

    // Set the directory where the JAR file will be placed
    destinationDirectory.set(file("$buildDir/libs"))

    // Configure the manifest file of the JAR
    manifest {
        // Add the Main-Class attribute to the manifest.
        // This tells the JVM which class contains the main method to run.
        attributes("Main-Class" to "io.github.cpsc559.team16.client.Client")
    }
}

//
//// Task to create a fat/uber JAR (includes dependencies for standalone execution)
//tasks.register<Jar>("clientFatJar") {
//    group = "build"
//    description = "Creates a runnable JAR for the Client application."
//
//    // Set the output file name to "client.jar"
//    archiveFileName.set("client.jar")
//    // Place the JAR in the build/libs directory
//    destinationDirectory.set(file("$buildDir/libs"))
//
//    // Include the compiled classes of this module
//    from(sourceSets.main.get().output)
//
//    // Include all runtime dependencies by unpacking them into the JAR
//    from({
//        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
//    })
//
//    // Avoid duplicate files
//    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
//
//    // Ensure the manifest contains the Main-Class attribute so the JAR is executable
//    manifest {
//        attributes("Main-Class" to "io.github.cpsc559.team16.client.Client")
//    }
//}

tasks.register<Jar>("clientFatJar") {
    group = "build"
    description = "Creates a runnable JAR for the Client application."

    // Set the output file name to "client.jar"
    archiveFileName.set("client.jar")
    // Place the JAR in the build/libs directory
    destinationDirectory.set(file("$buildDir/libs"))

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
        attributes("Main-Class" to "io.github.cpsc559.team16.client.Client")
    }
}



// Task to build and package the JAR before creating the Docker image
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
        println("Client JAR successfully created: $buildDir/libs/client.jar")
    }
}

// Define a custom Gradle task to build the Client Docker image
tasks.register<DockerBuildImage>("buildClientImage") {
    group = "docker-client"                          // Categorizes this task under the "docker" group when running `gradlew tasks` to see available tasks
    description = "Builds the Docker image for the client application."

    dependsOn("buildClientJar")                // Ensures the JAR is built before Docker runs (compiling everything in the container isn't ideal, especially if we want to spin up many processes)

    inputDir.set(file("."))			            // Sets the build context to the current directory (client module)
    dockerFile.set(file("Dockerfile"))              // Uses the `Dockerfile` located in this directory for building the image
    images.add("client:latest")                         // Tags the built Docker image as "client:latest"
}
//
//// Task to remove a container by name (string).
//tasks.register<DockerRemoveContainer>("removeClientContainer") {
//    group = "docker-client"
//    description = "Removes the existing client container (client_container) if it exists."
//
//    // Target the container by its fixed name.
//    targetContainerId("client_container")
//
//    // Force removal even if the container is running.
//    force.set(true)
//}

// Helper function to run a command and return its output as a String
fun String.runCommand(): String {
    return ProcessBuilder(*this.split(" ").toTypedArray())
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText()
}

// Define a task to safely remove the client container if it exists
tasks.register("safeRemoveClientContainer") {
    group = "docker-client"
    description = "Removes the client container (client_container) if it exists, otherwise does nothing."

    doLast {
        // Execute the Docker command to list containers with the name client_container.
        // The '^/client_container$' filter ensures an exact match.
        val result = "docker ps -a --filter \"name=^/client_container\$\" --format \"{{.ID}}\"".runCommand()
        if (result.trim().isNotEmpty()) {
            println("Container 'client_container' exists. Removing it...")
            // Remove the container forcefully (even if running)
            val removeResult = "docker rm -f client_container".runCommand()
            println("Removal output: $removeResult")
        } else {
            println("No container named 'client_container' found; nothing to remove.")
        }
    }
}


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

    dependsOn("safeRemoveClientContainer","buildClientImage")    // calls the task to create an image if one does not already exist
    imageId.set("client:latest")            // Uses the image built from the Dockerfile
    containerName.set("client_container")   // Default docker behavior assigns a random container name

    // ---------- WE CAN DEFINE PORT BINDING HERE ----------------
    // hostConfig.portBindings.set(listOf("8080:8080")) // Port binding (if needed)

    // Printing the container ID to console. Can be removed if desired.
    doLast {
        println("Client Container Built - Name: ${containerName.get()}")
    }
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

// Task to start the local client container - and build one if it doesn't exist.
tasks.register<DockerStartContainer>("startClientContainer") {
    group = "docker-client"
    description = "Starts the most recently created client container <client_container>"

    dependsOn(clientContainer) // Ensure the container is created first
    // Since we will only need one client, it is unlikely we need multiple containers per machine.
    targetContainerId("client_container")
}

// Task to start a Client Container and log its output.
tasks.register("runClient") {
    group = "docker-client"
    description = "Starts the client container and streams its logs."
    // This wrapper task depends on both starting the container and then streaming logs.
    dependsOn("startClientContainer")
    dependsOn("streamClientLogs")
}