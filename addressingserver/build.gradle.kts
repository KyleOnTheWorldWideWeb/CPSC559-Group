// Import Docker tasks from the bmuschko plugin for image building and container management
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import java.util.Properties  // Used for loading .env file for port bindings
import org.gradle.api.tasks.TaskAction

// Applying necessary plugins for Java application development and Docker support
plugins {
    id("java")                               // Enables Java support for this Gradle module
    id("application")                        // Defines this project as an application with a main entry point
    id("com.bmuschko.docker-remote-api")     // Adds Docker support via the bmuschko Gradle plugin. This automates Docker builds.
}

/*
    Declaring dependencies for the addressingserver module.
*/
dependencies {
    implementation(project(":common"))                                   // Dependency on the common module (utilities, exceptions)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")    // JSON support
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")            // JUnit 5 for testing
}

// Configure the application plugin with the main class for launching the addressingserver.
application {
    mainClass.set("io.github.cpsc559.team16.addressingserver.AddressingServer")
}

// Loading the port bindings from the addressingserver .env file
val envProperties = Properties().apply {
    file(".env").inputStream().use { load(it) }
}

// Configure the default jar task to build the addressingserver JAR file
tasks.jar {
    archiveFileName.set("addressingserver.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.addressingserver.AddressingServer")
    }
}

// Task to create a fat (uber) JAR that packages the application along with all its dependencies
tasks.register<Jar>("addressingserverFatJar") {
    group = "build"
    description = "Creates a runnable JAR for the AddressingServer application."
    archiveFileName.set("addressingserver.jar")
    
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    // Include the compiled classes of this module (addressingserver)
    from(sourceSets.main.get().output)
    
    // Explicitly include the compiled classes from the common module
    from(project(":common").sourceSets.main.get().output)
    
    // Include all runtime dependencies by unpacking their JARs
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")

    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.addressingserver.AddressingServer")
    }
}

// Task to build and package the addressingserver JAR before the Docker image is created.
tasks.register("buildAddrServerJar") {
    group = "build"
    description = "Compiles and packages the addressingserver.jar before the Docker image is built."
    dependsOn("addressingserverFatJar")
    doLast {
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        println("Addressing Server module JAR successfully created: ${libsDir}/addressingserver.jar")
    }
}

// Task to build the Docker image for the AddrServer application.
tasks.register<DockerBuildImage>("buildAddrServerImage") {
    group = "docker-addressing_server"
    description = "Builds the Docker image for the AddressingServer application."
    
    dependsOn("buildAddrServerJar")
    
    inputDir.set(file("."))           // Use the module directory as the Docker build context
    dockerFile.set(file("Dockerfile")) // Use the Dockerfile in this directory
    images.add("addrserver:latest") // Tag the image as addrserver:latest
    
    doLast {
        try {
            val imageInfo = dockerClient.inspectImageCmd("addrserver:latest").exec()
            println("Previous addressingserver Image ID: ${imageInfo.id}\n")
        } catch (e: Exception) {
            println("No previous image found for 'addrserver:latest'. A new image will be created.")
        }
    }
}

// Task to remove the Docker image if it exists.
tasks.register<DockerRemoveImage>("safeRemoveAddrServerImage") {
    group = "docker-addressing_server"
    description = "Removes the Docker image <addrserver:latest> if it exists."
    targetImageId("addrserver:latest")
    force.set(true)
    onlyIf {
        try {
            val imageInfo = dockerClient.inspectImageCmd("addrserver:latest").exec()
            imageInfo != null
        } catch (e: Exception) {
            logger.error("Image 'addrserver:latest' does not exist: ${e.message}")
            false
        }
    }
    doLast{
        println("Successfully removed previous addrserver:latest image.")
    }
}

// Task to kill the AddrServer container if it is running.
tasks.register<DockerKillContainer>("killAddrServerContainer") {
    group = "docker-addressing_server"
    description = "Kills the AddrServer container (addrserver_container) if it is running."
    targetContainerId("addrserver_container")
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("addrserver_container").exec()
            containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'addrserver_container' has been killed.")
    }
}

// Task to remove the addressingserver container if it exists and is not running.
tasks.register<DockerRemoveContainer>("removeAddrServerContainer") {
    group = "docker-addressing_server"
    description = "Removes the AddrServer container if it is not running."
    targetContainerId("addrserver_container")
    force.set(true)
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("addrserver_container").exec()
            !containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'addrserver_container' has been removed.")
    }
}

// Composite task to safely remove the addressingserver container.
tasks.register("safeRemoveAddrServerContainer") {
    group = "docker-addressing_server"
    description = "Kills the addressingserver container if running; otherwise removes it if it exists."
    dependsOn("killAddrServerContainer", "removeAddrServerContainer")
}

// Create the addressingserver container from the built image.
val addrServerContainer = tasks.register<DockerCreateContainer>("buildAddrServerContainer") {
    group = "docker-addressing_server"
    description = "Creates a Docker container using the latest addressingserver image (addrserver:latest)"
    dependsOn("safeRemoveAddrServerContainer", "buildAddrServerImage")
    imageId.set("addrserver:latest")
    containerName.set("addrserver_container")

    // ---------- WE CAN DEFINE PORT BINDING HERE ----------------
    hostConfig.portBindings.set(
            listOf(
                    "${envProperties.getProperty("CLIENT_PORT")}:${envProperties.getProperty("CLIENT_PORT")}",
                    "${envProperties.getProperty("REPLICA_PORT")}:${envProperties.getProperty("REPLICA_PORT")}",
                    "${envProperties.getProperty("CHAT_SERVER_PORT")}:${envProperties.getProperty("CHAT_SERVER_PORT")}"
            )
    )
    // Printing the container name and image ID to console
    doLast {
        println("addressingserver Container built - Name: ${containerName.get()}")
    }
}

// Ensures ll tasks that involve running a container (begin with `run`) are configured with the module specific .env file
// TODO - We can remove this once/if we start using Docker Compose as it does it all for us!
//tasks.matching { it.name.startsWith("run") }.configureEach {
//    doFirst {
//        def envFile = file('.env')
//        if (envFile.exists()) {
//            envFile.eachLine { line ->
//                line = line.trim()
//                if (!line || line.startsWith('#')) {
//                    return
//                }
//                def parts = line.split('=', 2)
//                if (parts.size() == 2) {
//                    def key = parts[0].trim()
//                    def value = parts[1].trim()
//                    environment key, value
//                }
//            }
//        } else {
//            println ".env file not found, skipping environment variables load."
//        }
//    }
//}


// Ensure the container is created after the image is built.
tasks.named("buildAddrServerContainer") {
    mustRunAfter("buildAddrServerImage")
}

// Task to stream logs from the addressingserver container.
tasks.register<DockerLogsContainer>("streamAddrServerLogs") {
    group = "docker-addressing_server"
    description = "Streams logs from the addressingserver container to the console."
    targetContainerId("addrserver_container")
    follow.set(true)
}


// Task to start the AddrServer container.
tasks.register<DockerStartContainer>("startNewAddrServerContainer") {
    group = "docker-addressing_server"
    description = "Builds a new container <addrserver_container> and starts it."
    dependsOn(addrServerContainer)
    targetContainerId("addrserver_container")
}

/*
    Task to delete the current client container and build a new one while retaining the addressing server Image on disk.
    If there have been any changes to classes in the module the container will be built from a NEW image.
*/
tasks.register("runAddrServerRetainImg") {
    group = "docker-addressing_server"
    description = "Builds and runs an Addressing Server Container from the current Image - deletes the current Container but preserves the Image.\n" +
            "\t\t\t(Dockerfile -> AddrServer Image -> AddrServer Container)."
    // This wrapper task depends on starting the container and then telling gradle to stream its logs.
    dependsOn("safeRemoveAddrServerContainer", "startNewAddrServerContainer","streamAddrServerLogs")
    dependsOn()
    doLast {
        println("Addressing Server container started from image 'addrserver:latest'.")
    }
}

/*
This composite task builds a new container from scratch (Dockerfile -> addrserver:latest Image -> addrserver_container Container).
This task always builds a new addressingserver Docker Image and removes the old one from disk.
*/
tasks.register("runAddrServerWipeImg") {
    group = "docker-addressing_server"
    description = "Builds and runs an Addressing Server Container from a new Image - deletes the current Image and Container in the local directory.\n"+
            "\t\t\t(Dockerfile -> AddrServer Image -> AddrServer Container)."
    dependsOn("safeRemoveAddrServerContainer", "safeRemoveAddrServerImage")
    dependsOn("startNewAddrServerContainer", "streamAddrServerLogs")
    doLast {
        println("Addressing Server container started from new image 'addrserver:latest'. Previous image removed from disk.")
    }
}