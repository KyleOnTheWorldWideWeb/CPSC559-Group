// Import Docker tasks from the bmuschko plugin for image building and container management
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.TaskAction
import java.io.File

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
    implementation(project(":common"))                                   // Adds dependency on the utilities module
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")    // JSON support
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")            // JUnit 5 testing framework
}

// Configure the application plugin with the main class for launching the app - ChatServer.java in this case
application {
    mainClass.set("io.github.cpsc559.team16.chatserver.ChatServer")
}

// Configure the default jar task to build the chatserver JAR file
tasks.jar {

    // Set the output file name for the JAR
    archiveFileName.set("chatserver.jar")

    // Set the directory where the JAR file will be placed
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.chatserver.ChatServer")
    }
}

// Task to create a fat (uber) JAR that packages the application along with all its dependencies
tasks.register<Jar>("chatserverFatJar") {
    group = "build"
    description = "Creates a runnable JAR for the ChatServer application."

    archiveFileName.set("chatserver.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    from(sourceSets.main.get().output)
    // Explicitly include the compiled classes from the common module
    from(project(":common").sourceSets.main.get().output)

    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")

    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.chatserver.ChatServer")
    }
}

tasks.register("buildChatServerJar") {
    group = "build"
    description = "Compiles and packages the chatserver.jar before the Docker image is built."

    dependsOn("chatserverFatJar")

    doLast {
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        println("ChatServer JAR successfully created: ${libsDir}/chatserver.jar")
    }
}

// Task to build the Docker image for the chatserver application using the bmuschko Docker plugin.
tasks.register<DockerBuildImage>("buildChatServerImage") {
    group = "docker-chatserver"
    description = "Builds the Docker image for the chatserver application."

    dependsOn("buildChatServerJar")

    inputDir.set(file("."))
    dockerFile.set(file("Dockerfile"))
    images.add("chatserver:latest")
}

// Task to safely remove an existing Docker container named "chatserver_container" if it exists.
tasks.register<DockerRemoveImage>("safeRemoveChatServerImage") {
    group = "docker-chatserver"
    description = "Removes the Docker image <chatserver:latest> if it exists."

    targetImageId("chatserver:latest")
    force.set(true)

    onlyIf {
        try {
            val imageInfo = dockerClient.inspectImageCmd("chatserver:latest").exec()
            imageInfo != null
        } catch (e: Exception) {
            logger.error("Image 'chatserver:latest' does not exist: ${e.message}")
            false
        }
    }

    doLast {
        println("Docker image chatserver:latest removed.")
    }
}

// Task to remove chatserver_container if it is running.
tasks.register<DockerKillContainer>("killChatServerContainer") {
    group = "docker-chatserver"
    description = "Kills the chatserver container (chatserver_container) if it exists."

    targetContainerId("chatserver_container")

    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("chatserver_container").exec()
            containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'chatserver_container' has been killed.")
    }
}

// Task to remove chatserver_container if it exists and is not running.
tasks.register<DockerRemoveContainer>("removeChatServerContainer") {
    group = "docker-chatserver"
    description = "Removes the chatserver container if it is not running."

    targetContainerId("chatserver_container")
    force.set(true)

    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("chatserver_container").exec()
            !containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'chatserver_container' has been removed.")
    }
}

tasks.register("safeRemoveChatServerContainer") {
    group = "docker-chatserver"
    description = "Kills the chatserver container if running; otherwise removes it if it exists."
    dependsOn("killChatServerContainer", "removeChatServerContainer")
}

val chatserverContainer = tasks.register<DockerCreateContainer>("buildChatServerContainer") {
    group = "docker-chatserver"
    description = "Creates a Docker container using the latest chatserver image (chatserver:latest)"

    dependsOn("safeRemoveChatServerContainer")
    imageId.set("chatserver:latest")
    containerName.set("chatserver_container")

    // Add environment variables from the .env file
    val envFile = File(".env")
    if (envFile.exists()) {
        envFile.forEachLine { line ->
            val (key, value) = line.split("=")
            envVars.put(key, value)
        }
    }

    // Map the port from the environment variable
    val port = envVars.get().getOrDefault("PORT", "2424")
    hostConfig.portBindings.set(listOf("$port:$port"))

    doLast {
        println("ChatServer Container Built - Name: ${containerName.get()}")
    }
}

tasks.named("buildChatServerContainer") {
    mustRunAfter("buildChatServerImage")
}

tasks.register<DockerLogsContainer>("streamChatServerLogs") {
    group = "docker-chatserver"
    description = "Streams logs from the chatserver container to the console."

    targetContainerId("chatserver_container")
    follow.set(true)
}

tasks.register<DockerStartContainer>("startNewChatServerContainer") {
    group = "docker-chatserver"
    description = "Builds a new container <chatserver_container> and starts it."

    dependsOn(chatserverContainer)
    targetContainerId("chatserver_container")
}

tasks.register("runChatServerRetainImg") {
    group = "docker-chatserver"
    description = "Builds a new container from the <chatserver:latest> Image and starts it."
    dependsOn("safeRemoveChatServerContainer", "buildChatServerImage", "startNewChatServerContainer", "streamChatServerLogs")
    doLast {
        println("ChatServer container started with image 'chatserver:latest'.")
    }
}

tasks.register("runChatServerWipeImg") {
    mustRunAfter("safeRemoveChatServerContainer", "safeRemoveChatServerImage")
    group = "docker-chatserver"
    description = "Builds a new container from scratch (Dockerfile->ChatServer Image->ChatServer Container)"
    dependsOn("safeRemoveChatServerContainer", "safeRemoveChatServerImage")
    dependsOn("buildChatServerImage", "startNewChatServerContainer", "streamChatServerLogs")
}