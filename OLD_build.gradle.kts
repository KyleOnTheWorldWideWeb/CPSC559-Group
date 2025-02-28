plugins {
    id("java")
    id("com.bmuschko.docker-remote-api") version "9.4.0" // Actively maintained Docker plugin
}

java {
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src"))  
        }
        named("test") {
            java.setSrcDirs(listOf("tests")) 
        }
    }
}

repositories {
    mavenCentral() // Repository used for Java dependencies. Actively maintained
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2") // JSON support (most recent version)
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0") // JUnit 5.12 for testing
}

// Ensuring Gradle uses the most recent version of JUnit
tasks.named<Test>("test") {
    useJUnitPlatform()
}

// ---------- Chat Server ----------

// Gradle task to automate removal of previous Docker Images.
// Docker does not automatically garbage collect deprecated Images.
tasks.register("removeOldChatServerImage") {
    doLast {
        exec {
            commandLine("docker", "rmi", "-f", "cpsc559/team16-chatserver:latest")
        }
    }
}

// Gradle Task to build the Chat Server Docker image
tasks.register<com.bmuschko.gradle.docker.tasks.image.DockerBuildImage>("buildChatServerDockerImage") {
    dependsOn("removeOldChatServerImage")
    inputDir.set(file("."))
    dockerFile.set(file("docker/chatserver/Dockerfile"))
    images.add("cpsc559/team16-chatserver:latest")
}

// Task to create a Docker container for the Chat Server
tasks.register<com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer>("createChatServerContainer") {
    dependsOn("buildChatServerDockerImage")
    targetImageId.set("cpsc559/team16-chatserver:latest") // ✅ Fixed
    hostConfig.portBindings.set(listOf("8080:8080"))
}

// Task to start the Chat Server Docker container
tasks.register<com.bmuschko.gradle.docker.tasks.container.DockerStartContainer>("startChatServerContainer") {
    dependsOn("createChatServerContainer")
    targetContainerId.set(tasks.named<com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer>("createChatServerContainer").map { it.containerId.get() }) // ✅ Fixed
}

// ---------- Addressing Server ----------

tasks.register("removeOldAddrsServerImage") {
    doLast {
        exec {
            commandLine("docker", "rmi", "-f", "cpsc559/team16-addressingserver:latest")
        }
    }
}

// Gradle Task to build the Addressing Server Docker image
tasks.register<com.bmuschko.gradle.docker.tasks.image.DockerBuildImage>("buildAddressingServerDockerImage") {
    dependsOn("removeOldAddrsServerImage")
    inputDir.set(file("."))
    dockerFile.set(file("docker/addressingserver/Dockerfile"))
    images.add("cpsc559/team16-addressingserver:latest")
}

// Task to create a Docker container for the Addressing Server
tasks.register<com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer>("createAddressingServerContainer") {
    dependsOn("buildAddressingServerDockerImage")
    targetImageId.set("cpsc559/team16-addressingserver:latest") // ✅ Fixed
    hostConfig.portBindings.set(listOf("5353:5353"))
}

// Task to start the Addressing Server Docker container
tasks.register<com.bmuschko.gradle.docker.tasks.container.DockerStartContainer>("startAddressingServerContainer") {
    dependsOn("createAddressingServerContainer")
    targetContainerId.set(tasks.named<com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer>("createAddressingServerContainer").map { it.containerId.get() }) // ✅ Fixed
}

// ---------- Client ----------

tasks.register("removeOldClientImage") {
    doLast {
        exec {
            commandLine("docker", "rmi", "-f", "cpsc559/team16-client:latest")
        }
    }
}

// Gradle Task to build the Client Docker image
tasks.register<com.bmuschko.gradle.docker.tasks.image.DockerBuildImage>("buildClientDockerImage") {
    dependsOn("removeOldClientImage")
    inputDir.set(file("."))
    dockerFile.set(file("docker/client/Dockerfile"))
    images.add("cpsc559/team16-client:latest")
}

// Task to create a Docker container for the Client
tasks.register<com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer>("createClientContainer") {
    dependsOn("buildClientDockerImage")
    targetImageId.set("cpsc559/team16-client:latest") // ✅ Fixed
    hostConfig.portBindings.set(listOf("8081:8081"))
}

// Task to start the Client Docker container
tasks.register<com.bmuschko.gradle.docker.tasks.container.DockerStartContainer>("startClientContainer") {
    dependsOn("createClientContainer")
    targetContainerId.set(tasks.named<com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer>("createClientContainer").map { it.containerId.get() }) // ✅ Fixed
}



// Apply the Gradle Docker Compose Plugin
plugins {
    id("java") // If you are using Java
    id("com.avast.gradle.docker-compose") version "0.17.6" // Docker Compose Plugin
}

// Import necessary classes
import com.avast.gradle.dockercompose.ComposeExtension
        import java.time.Duration


// Configure Docker Compose Plugin
        dockerCompose {
            useComposeFiles.set(listOf("docker-compose.yml")) // Specify your compose file(s)

            // Ensures Docker Compose is executed before tests
            isRequiredBy(tasks.test)

            // Additional configurations
            waitForTcpPorts.set(true) // Wait for ports before proceeding
            waitForTcpPortsTimeout.set(Duration.ofMinutes(10)) // ✅ Fix: No `java.` prefix needed
            stopContainers.set(true) // Stop containers after task execution
            removeContainers.set(true) // Remove stopped containers
        }

// Task to bring up Docker services before running tests
tasks.test {
    dependsOn("composeUp")
    finalizedBy("composeDown")
}
