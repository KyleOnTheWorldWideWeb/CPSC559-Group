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
    id("com.bmuschko.docker-remote-api")     // Adds Docker support via the bmuschko Gradle plugin
}

/*
    Declaring dependencies for the test module.
    Adjust or add any additional dependencies for your integration/system tests here.
*/
dependencies {
    implementation(project(":common"))
    implementation(project(":common"))
    implementation(project(":chatserver"))
    implementation(project(":addressingserver"))
    implementation(project(":client"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
}

application {
    mainClass.set("io.github.cpsc559.team16.addressingservertests.TestAddressingServer")
}

// Load the port bindings from the test module’s .env file
val envProperties = Properties().apply {
    file(".env").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Configure the default jar task to build the test module’s JAR file
tasks.jar {
    archiveFileName.set("addressingserver-tests.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes(
                "Main-Class" to "io.github.cpsc559.team16.addressingservertests.TestAddressingServer"
        )
    }
}

// Task to create a fat (uber) JAR that packages the test application along with all its dependencies
tasks.register<Jar>("addressingserverTestsFatJar") {
    group = "build"
    description = "Creates a runnable JAR for the AddressingServer tests."
    archiveFileName.set("addressingserver-tests.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    // Include the compiled classes of this test module
    from(sourceSets.main.get().output)

    // Optionally include the compiled classes from the common module
    from(project(":common").sourceSets.main.get().output)

    // Include all runtime dependencies by unpacking their JARs
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")

    manifest {
        attributes(
                "Main-Class" to "io.github.cpsc559.team16.addressingservertests.TestAddressingServer"
        )
    }
}

// Task to build and package the tests JAR before the Docker image is created.
tasks.register("buildTestAddrServerJar") {
    group = "build"
    description = "Compiles and packages the addressingserver-tests.jar before the Docker image is built."
    dependsOn("addressingserverTestsFatJar")
    doLast {
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        println("Addressing Server Tests JAR successfully created: ${libsDir}/addressingserver-tests.jar")
    }
}

// Task to build the Docker image for the test application.
tasks.register<DockerBuildImage>("buildTestAddrServerImage") {
    group = "docker-addressing_server_tests"
    description = "Builds the Docker image for the AddressingServer tests."
    dependsOn("buildTestAddrServerJar")

    inputDir.set(file("."))            // Use the module directory as the Docker build context
    dockerFile.set(file("Dockerfile")) // Dockerfile must be in this module folder
    images.add("testaddrserver:latest")

    doLast {
        try {
            val imageInfo = dockerClient.inspectImageCmd("testaddrserver:latest").exec()
            println("Previous testaddrserver Image ID: ${imageInfo.id}\n")
        } catch (e: Exception) {
            println("No previous image found for 'testaddrserver:latest'. A new image will be created.")
        }
    }
}

// Task to remove the Docker image if it exists.
tasks.register<DockerRemoveImage>("safeRemoveTestAddrServerImage") {
    group = "docker-addressing_server_tests"
    description = "Removes the Docker image <testaddrserver:latest> if it exists."
    targetImageId("testaddrserver:latest")
    force.set(true)
    onlyIf {
        try {
            val imageInfo = dockerClient.inspectImageCmd("testaddrserver:latest").exec()
            imageInfo != null
        } catch (e: Exception) {
            logger.error("Image 'testaddrserver:latest' does not exist: ${e.message}")
            false
        }
    }
    doLast {
        println("Successfully removed previous testaddrserver:latest image.")
    }
}

// Task to kill the test container if it is running.
tasks.register<DockerKillContainer>("killTestAddrServerContainer") {
    group = "docker-addressing_server_tests"
    description = "Kills the test container (testaddrserver_container) if it is running."
    targetContainerId("testaddrserver_container")
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("testaddrserver_container").exec()
            containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'testaddrserver_container' has been killed.")
    }
}

// Task to remove the test container if it exists and is not running.
tasks.register<DockerRemoveContainer>("removeTestAddrServerContainer") {
    group = "docker-addressing_server_tests"
    description = "Removes the testaddrserver_container if it is not running."
    targetContainerId("testaddrserver_container")
    force.set(true)
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("testaddrserver_container").exec()
            !containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'testaddrserver_container' has been removed.")
    }
}

// Composite task to safely remove the test container.
tasks.register("safeRemoveTestAddrServerContainer") {
    group = "docker-addressing_server_tests"
    description = "Kills the testaddrserver_container if running; otherwise removes it if it exists."
    dependsOn("killTestAddrServerContainer", "removeTestAddrServerContainer")
}

// Create the test container from the built image.
val testAddrServerContainer = tasks.register<DockerCreateContainer>("buildTestAddrServerContainer") {
    group = "docker-addressing_server_tests"
    description = "Creates a Docker container using the latest testaddrserver image (testaddrserver:latest)."
    dependsOn("safeRemoveTestAddrServerContainer", "buildTestAddrServerImage")
    imageId.set("testaddrserver:latest")
    containerName.set("testaddrserver_container")

    // Map ports, if your TestAddressingServer also needs them from .env
    hostConfig.portBindings.set(
            listOf(
                    "${envProperties.getProperty("CLIENT_PORT")}:${envProperties.getProperty("CLIENT_PORT")}",
                    "${envProperties.getProperty("REPLICA_PORT")}:${envProperties.getProperty("REPLICA_PORT")}",
                    "${envProperties.getProperty("CHAT_SERVER_PORT")}:${envProperties.getProperty("CHAT_SERVER_PORT")}"
            )
    )

    doLast {
        println("TestAddressingServer container built - Name: ${containerName.get()}")
    }
}

// Ensure the container is created after the image is built
tasks.named("buildTestAddrServerContainer") {
    mustRunAfter("buildTestAddrServerImage")
}

// Task to stream logs from the test container
tasks.register<DockerLogsContainer>("streamTestAddrServerLogs") {
    group = "docker-addressing_server_tests"
    description = "Streams logs from the testaddrserver_container to the console."
    targetContainerId("testaddrserver_container")
    follow.set(true)
}

// Task to start the test container
tasks.register<DockerStartContainer>("startNewTestAddrServerContainer") {
    group = "docker-addressing_server_tests"
    description = "Builds a new container <testaddrserver_container> and starts it."
    dependsOn(testAddrServerContainer)
    targetContainerId("testaddrserver_container")
}

// Composite task to remove container but keep the image
tasks.register("runTestAddrServerRetainImg") {
    group = "docker-addressing_server_tests"
    description = "Builds and runs a TestAddressingServer Container from the current Image. Deletes the current container but preserves the image."
    dependsOn("safeRemoveTestAddrServerContainer", "startNewTestAddrServerContainer", "streamTestAddrServerLogs")
    doLast {
        println("TestAddressingServer container started from image 'testaddrserver:latest'.")
    }
}

// Composite task to remove both container and image, then build fresh
tasks.register("runTestAddrServerWipeImg") {
    group = "docker-addressing_server_tests"
    description = "Builds and runs a TestAddressingServer container from a new image. Deletes the current container and image."
    dependsOn("safeRemoveTestAddrServerContainer", "safeRemoveTestAddrServerImage")
    dependsOn("startNewTestAddrServerContainer", "streamTestAddrServerLogs")
    doLast {
        println("TestAddressingServer container started from new image 'testaddrserver:latest'. Previous image removed from disk.")
    }
}
