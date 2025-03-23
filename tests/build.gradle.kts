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
    // Project module dependencies
    implementation(project(":common"))
    implementation(project(":chatserver"))
    implementation(project(":addressingserver"))
    implementation(project(":client"))
    // Jackson (for JSON serialization - how we send object over the network)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    // J-Unit API & Test Engine
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    // Mockito for mocking objects in tests (testing classes declared as final - i.e. they cannot be extended and easily tested)
    testImplementation("org.mockito:mockito-core:5.7.0")
    // Mockito support for JUnit 5 (allows @ExtendWith(MockitoExtension.class))
    testImplementation("org.mockito:mockito-inline:5.2.0")
}


application {
    mainClass.set("io.github.cpsc559.team16.tests.TestRunner")
}

// Loading the port bindings from the .env file in the common module directory
val envProperties = Properties().apply {
    file(".env").inputStream().use { load(it) }
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

    //Configuring interactive settings
    hostConfig.apply {
        tty.set(true)  // Allocate a TTY for interactive shell
        stdinOpen.set(true)  // Keep STDIN open for interactive input
    }

    // >---------------- WE CAN DEFINE PORT BINDING AND NETWORKS HERE ---------------------<
    //hostConfig.network.set("my-macvlan-network")
    hostConfig.portBindings.set(
            listOf(
                    "${envProperties.getProperty("AS_CLIENT_PORT")}:${envProperties.getProperty("AS_CLIENT_PORT")}",
                    "${envProperties.getProperty("AS_REPLICA_PORT")}:${envProperties.getProperty("AS_REPLICA_PORT")}",
                    "${envProperties.getProperty("AS_CHATSERVER_PORT")}:${envProperties.getProperty("AS_CHATSERVER_PORT")}",
                    "${envProperties.getProperty("CS_CLIENT_PORT")}:${envProperties.getProperty("CS_CLIENT_PORT")}",
                    "${envProperties.getProperty("CS_PEER_PORT")}:${envProperties.getProperty("CS_PEER_PORT")}",
                    "${envProperties.getProperty("CS_ADDRSERVER_PORT")}:${envProperties.getProperty("CS_ADDRSERVER_PORT")}",
                    "${envProperties.getProperty("CLIENT_ADDRSERVER_PORT")}:${envProperties.getProperty("CLIENT_ADDRSERVER_PORT")}",
                    "${envProperties.getProperty("CLIENT_CHATSERVER_PORT")}:${envProperties.getProperty("CLIENT_CHATSERVER_PORT")}"
            )
    )
    println("AS_CLIENT_PORT=${envProperties.getProperty("AS_CLIENT_PORT")}")
    println("AS_REPLICA_PORT=${envProperties.getProperty("AS_REPLICA_PORT")}")
    println("AS_CHATSERVER_PORT=${envProperties.getProperty("AS_CHATSERVER_PORT")}\n")
    println("CS_CLIENT_PORT=${envProperties.getProperty("CS_CLIENT_PORT")}")
    println("CS_PEER_PORT=${envProperties.getProperty("CS_PEER_PORT")}")
    println("CS_ADDRSERVER_PORT=${envProperties.getProperty("CS_ADDRSERVER_PORT")}\n")
    println("CLIENT_ADDRSERVER_PORT=${envProperties.getProperty("CLIENT_ADDRSERVER_PORT")}")
    println("CLIENT_CHATSERVER_PORT=${envProperties.getProperty("CLIENT_CHATSERVER_PORT")}")

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


// Task to start the AddrServer container.
tasks.register<DockerStartContainer>("startNewTestContainer") {
    group = "docker-tests"
    description = "Builds a new container <test_container> and starts it."
    dependsOn(testContainer)
    targetContainerId("test_container")

    doLast {
        println("Successfully started test_container with automated platform independant Gradle tasks.")
    }
}

tasks.register("runTest") {
    dependsOn("safeRemoveTestContainer", "safeRemoveTestImage")
    dependsOn("startNewTestContainer") // Actually start the container with port bindings

    doLast {
        println("Launching a new Test container.")

    }
}

// >-------------------- TASKS FOR OPENING NEW TERMINAL WHEN RUNNING A NEW CONTAINER ------------------<
tasks.register("runTestWindows") {
    dependsOn("safeRemoveTestContainer", "safeRemoveTestImage")
    dependsOn("startNewTestContainer") // Actually start the container with port bindings

    doLast {
        println("Launching a new Windows CMD and attaching interactively...")
        // Attach to the running container's shell in a new terminal
        val attachCommand = "docker attach test_container"
        project.exec {
            commandLine("cmd", "/c", "start", "cmd", "/k", attachCommand)
        }

    }
}

tasks.register("runTestMacOS") {
    group = "docker-tests"
    description = "Builds and runs a Test container in a new macOS Terminal."

    dependsOn("safeRemoveTestContainer", "safeRemoveTestImage")
    dependsOn("startNewTestContainer")

    doLast {
        println("Launching a new macOS Terminal and attaching interactively...")

        val attachCommand = "docker attach test_container"

        // Open a new macOS Terminal window and run the attach command.
        project.exec {
            commandLine("osascript", "-e", "tell application \"Terminal\" to do script \"$attachCommand\"")
        }
    }
}

tasks.register("runTestLinux") {
    group = "docker-tests"
    description = "WARNING: For 1337∪$3R only. Builds and runs a Test container in a new Linux terminal."

    dependsOn("safeRemoveTestContainer", "safeRemoveTestImage")
    dependsOn("startNewTestContainer")

    doLast {
        println("Launching a new Linux terminal and attaching interactively...")

        val attachCommand = "docker attach test_container"

        // For GNOME Terminal:
        project.exec {
            commandLine("gnome-terminal", "--", "bash", "-c", attachCommand)
        }

        // Alternative if using x-terminal-emulator:
        // project.exec {
        //     commandLine("x-terminal-emulator", "-e", attachCommand)
        // }
    }
}

// >-------------------- END OF TASKS FOR OPENING NEW TERMINAL WHEN RUNNING A NEW CONTAINER ------------------<


tasks.register<Test>("runNIOMessageTester") {
    group = "docker-tests"
    description = "Runs the J-Unit test cases for the NIO Tester."
    useJUnitPlatform()
    include("io/github/cpsc559/team16/tests/NIOMessageChannelTester.class") // Adjust package path
}

