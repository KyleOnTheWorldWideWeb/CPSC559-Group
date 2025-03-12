// Import Docker tasks from the bmuschko plugin for image building and container management
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
import java.util.Properties  // Used for loading .env file for port bindings

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
    implementation(project(":chatserver"))
    implementation(project(":addressingserver"))
    implementation(project(":client"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
}

application {
    mainClass.set("io.github.cpsc559.team16.tests.TestRunner")
}

// Load the port bindings from the test module’s .env file
val envProperties = Properties().apply {
    file(".env").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Configure the default jar task to build the test module’s JAR file
tasks.jar {
    archiveFileName.set("tests.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes(
                "Main-Class" to "io.github.cpsc559.team16.tests.TestRunner"
        )
    }
}

// Task to create a fat (uber) JAR that packages the test application along with all its dependencies
tasks.register<Jar>("testsFatJar") {
    group = "build"
    description = "Creates a runnable JAR for module testing."
    archiveFileName.set("tests.jar")
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
                "Main-Class" to "io.github.cpsc559.team16.tests.TestRunner"
        )
    }
}

// Task to build and package the tests JAR before the Docker image is created.
tasks.register("buildTestJar") {
    group = "build"
    description = "Compiles and packages the tests.jar before the Docker image is built."
    dependsOn("testsFatJar")
    doLast {
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        println("CPSC 559(Team 16) - Tests JAR successfully created: ${libsDir}/tests.jar")
    }
}

// Task to build the Docker image for the test application.
tasks.register<DockerBuildImage>("buildTestImage") {
    group = "docker-tests"
    description = "Builds the Docker image for running tests involving all modules in the repository."
    dependsOn("buildTestJar")

    inputDir.set(file("."))            // Use the module directory as the Docker build context
    dockerFile.set(file("Dockerfile")) // Dockerfile must be in this module folder
    images.add("test:latest")

    doLast {
        try {
            val imageInfo = dockerClient.inspectImageCmd("test:latest").exec()
            println("Previous test Image ID: ${imageInfo.id}\n")
        } catch (e: Exception) {
            println("No previous image found for 'test:latest'. A new image will be created.")
        }
    }
}

// Task to remove the Docker image if it exists.
tasks.register<DockerRemoveImage>("safeRemoveTestImage") {
    group = "docker-tests"
    description = "Removes the Docker image <test:latest> if it exists."
    targetImageId("test:latest")
    force.set(true)
    onlyIf {
        try {
            val imageInfo = dockerClient.inspectImageCmd("test:latest").exec()
            imageInfo != null
        } catch (e: Exception) {
            logger.error("Image 'test:latest' does not exist: ${e.message}")
            false
        }
    }
    doLast {
        println("Successfully removed previous test:latest image.")
    }
}

// Task to kill the test container if it is running.
tasks.register<DockerKillContainer>("killTestContainer") {
    group = "docker-tests"
    description = "Kills the test container (test_container) if it is running."
    targetContainerId("test_container")
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("test_container").exec()
            containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'test_container' has been killed.")
    }
}

// Task to remove the test container if it exists and is not running.
tasks.register<DockerRemoveContainer>("removeTestContainer") {
    group = "docker-tests"
    description = "Removes the test_container if it is not running."
    targetContainerId("test_container")
    force.set(true)
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("test_container").exec()
            !containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'test_container' has been removed.")
    }
}

// Composite task to safely remove the test container.
tasks.register("safeRemoveTestContainer") {
    group = "docker-tests"
    description = "Kills the test_container if running; otherwise removes it if it exists."
    dependsOn("killTestContainer", "removeTestContainer")
}

// Create the test container from the built image.
val testContainer = tasks.register<DockerCreateContainer>("buildTestContainer") {
    group = "docker-tests"
    description = "Creates a Docker container using the latest test image (test:latest)."
    dependsOn("safeRemoveTestContainer", "buildTestImage")
    imageId.set("test:latest")
    containerName.set("test_container")

    // Configuring interactive settings
    hostConfig.apply {
        tty.set(true)  // Allocate a TTY for interactive shell
        stdinOpen.set(true)  // Keep STDIN open for interactive input
    }

    entrypoint.set(listOf("java", "-jar", "tests.jar"))

    doLast {
        println("Test container built - Name: ${containerName.get()}")
    }
}


// Ensure the container is created after the image is built
tasks.named("buildTestContainer") {
    mustRunAfter("buildTestImage")
}

// Task to stream logs from the test container
tasks.register<DockerLogsContainer>("streamTestLogs") {
    group = "docker-tests"
    description = "Streams logs from the test_container to the console."
    targetContainerId("test_container")
    follow.set(true)
}

// Task to start the test container
tasks.register<DockerStartContainer>("startNewTestContainerMacOS") {
    group = "docker-tests"
    description = "Builds a new container <test_container> and starts it."
    dependsOn(testContainer)
    targetContainerId("test_container")
    // Ensure interactive mode
    doLast {
        println("Launching test container in a new macOS terminal...")

        val runCommand = "docker run --rm -it --name test_container test:latest"

        project.exec {
            commandLine("osascript", "-e", "tell application \"Terminal\" to do script \"$runCommand\"")
        }
    }

}

tasks.register("startNewTestContainerLinux") {
    group = "docker-tests"
    description = "Starts a test container in a new terminal window interactively."
    dependsOn("safeRemoveTestContainer", "buildTestImage")

    doLast {
        println("Launching test container in a new terminal window...")

        val runCommand = "docker run --rm -it --name test_container test:latest"

        // Linux (GNOME Terminal)
        project.exec {
            commandLine("gnome-terminal", "--", "bash", "-c", runCommand)
        }

        // Alternative for systems using `x-terminal-emulator`
        // project.exec {
        //     commandLine("x-terminal-emulator", "-e", runCommand)
        // }
    }
}

tasks.register("startNewTestContainerWindows") {
    group = "docker-tests"
    description = "Starts a test container in a new terminal window on Windows."
    dependsOn("safeRemoveTestContainer", "buildTestImage")

    doLast {
        println("Launching test container in a new terminal window...")

        val runCommand = "docker run --rm -it --name test_container test:latest"

        // For Windows CMD
        project.exec {
            commandLine("cmd", "/c", "start", "cmd", "/k", runCommand)
        }

        // For Windows PowerShell:
        // project.exec {
        //     commandLine("powershell", "-Command", "Start-Process", "powershell", "-ArgumentList", "'-NoExit', '-Command', '$runCommand'")
        // }
    }
}


// Composite task to remove container but keep the image
tasks.register("runTestRetainImgWindows") {
    group = "docker-tests"
    description = "Builds and runs a Test Container from the current Image. Deletes the current container but preserves the image."
    dependsOn("safeRemoveTestContainer", "startNewTestContainer", "streamTestLogs")
    doLast {
        println("Test container started from image 'test:latest'.")
    }
}

tasks.register("runTestWindows") {
    group = "docker-tests"
    description = "Builds and runs a Test container in a window that is like a terminal but not quite, for proprietary (money) reasons."
    dependsOn("safeRemoveTestContainer", "safeRemoveTestImage")
    dependsOn("startNewTestContainerWindows", "streamTestLogs")
    doLast {
        println("Test container started from new image 'test:latest'. Previous image removed from disk.")
    }
}

tasks.register("runTestMacOS") {
    group = "docker-tests"
    description = "Builds and runs a Test container on a Big Mac"
    dependsOn("safeRemoveTestContainer", "safeRemoveTestImage")
    dependsOn("startNewTestContainerWindows", "streamTestLogs")
    doLast {
        println("Test container started from new image 'test:latest'. Previous image removed from disk.")
    }
}

tasks.register("runTestComputerJesus") {
    group = "docker-tests"
    description = "Builds and runs a Test container in a bash terminal for Linux."
    dependsOn("safeRemoveTestContainer", "safeRemoveTestImage")
    dependsOn("startNewTestContainerWindows", "streamTestLogs")
    doLast {
        println("Test container started from new image 'test:latest'. Previous image removed from disk.")
    }
}





