// Import Docker tasks from the bmuschko plugin for image building and container management
import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
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

// // Loading the port bindings from the addressingserver .env file
// val envProperties = Properties().apply {
//     file(".env").inputStream().use { load(it) }
// }

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

tasks.register("buildAddressingServer") {
    group = "build"
    description = "Builds the addressing server with common library dependencies."
    dependsOn(":common:commonJar", "addressingserverFatJar")
}

// Task to build and package the addressingserver JAR before the Docker image is created.
tasks.register("buildAddrServerJar") {
    group = "build"
    description = "Compiles and packages the addressingserver.jar before the Docker image is built."
    dependsOn("addressingserverFatJar")
    doLast {
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        println("Addressing Server module fatJAR successfully created: ${libsDir}/addressingserver.jar")
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
    // >---------------- WE CAN DEFINE PORT BINDING AND NETWORKS HERE ---------------------<
    //hostConfig.network.set("my-macvlan-network")
    // ======================================================================================
    //  Removed this section to be handled in the docker compose file
    // hostConfig.portBindings.set(
    //         listOf(
    //                 "${envProperties.getProperty("AS_CLIENT_PORT")}:${envProperties.getProperty("AS_CLIENT_PORT")}",
    //                 "${envProperties.getProperty("AS_REPLICA_PORT")}:${envProperties.getProperty("AS_REPLICA_PORT")}",
    //                 "${envProperties.getProperty("AS_CHATSERVER_PORT")}:${envProperties.getProperty("AS_CHATSERVER_PORT")}"
    //         )
    // )
    // ADD THE REST OF THE ENVIRONMENT VARIABLES
    // withEnvVar("AS_ROLE", envProperties.getProperty("AS_ROLE"))
    // withEnvVar("HOST_ADDRESS", envProperties.getProperty("HOST_ADDRESS"))

    // println("AS_ROLE = ${envProperties.getProperty("AS_ROLE")}")
    // println("HOST_ADDRESS = ${envProperties.getProperty("HOST_ADDRESS")}")
    // println("AS_CLIENT_PORT = ${envProperties.getProperty("AS_CLIENT_PORT")}")
    // println("AS_REPLICA_PORT = ${envProperties.getProperty("AS_REPLICA_PORT")}")
    // println("AS_CHATSERVER_PORT = ${envProperties.getProperty("AS_CHATSERVER_PORT")}")
    // Printing the container name and image ID to console
    doLast {
        println("addressingserver Container built - Name: ${containerName.get()}")
    }
}



tasks.register<DockerCreateNetwork>("createIRCNetwork") {
    group = "docker"
    description = "Creates a custom Docker network for container communication with external sources."
    networkName.set("my-macvlan-network")
    ipam.driver.set("default")
}


tasks.register<DockerCreateNetwork>("createMyMacvlanNetwork") {
    group = ("docker")
    description = ("Creates a custom macvlan network that assigns external IP addresses.")
    networkName.set("my-macvlan-network")
    ipam.driver.set("macvlan") // Use the macvlan driver to get external IPs

    // Configure IPAM settings for the network.
    ipam.getDriver().set("default")

    // Optionally, add an IPAM configuration to define a subnet and gateway.
    ipam.configs.add(
            project.objects.newInstance(DockerCreateNetwork.Ipam.Config::class.java).apply {
                // Adjust these values to match your LAN configuration.
                setSubnet("192.168.1.0/24")
                setGateway("192.168.1.1")
            }
    )
}


// Ensure containers are created after the image is built.
tasks.configureEach {
    if (name in listOf("buildAddrServerContainer", "buildReplicaContainer")) {
        mustRunAfter("buildAddrServerImage")
    }
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

// >-------------------- TASKS FOR OPENING NEW TERMINAL WHEN RUNNING A NEW CONTAINER ------------------<
tasks.register("runAddrServerWindows") {
    group = "docker-addressing_server"
    description = "Does the exact same thing as runAddrServerWipeImg but opens a new terminal for the containers output!"
    dependsOn("safeRemoveAddrServerContainer", "safeRemoveAddrServerImage")
    dependsOn("startNewAddrServerContainer")

    doLast {
        println("Addressing Server container started from new image 'addrserver:latest'. Previous image removed from disk.")
        println("Launching a new Windows terminal.......")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")

        // Attach to the running container's shell in a new terminal
        val attachCommand = "docker attach addrserver_container"
        project.exec {
            commandLine("cmd", "/c", "start", "cmd", "/k", attachCommand)
        }

    }
}


tasks.register("runAddrServerMacOS") {
    group = "docker-addressing_server"
    description = "Does the same as runAddrServerWipeImg but opens a new terminal for the container output on macOS!"

    dependsOn("safeRemoveAddrServerContainer", "safeRemoveAddrServerImage")
    dependsOn("startNewAddrServerContainer")

    doLast {
        println("Addressing Server container started from new image 'addrserver:latest'. Previous image removed from disk.")
        println("Launching a new macOS terminal.......")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")

        // Attach to the running container in a new terminal
        val attachCommand = "docker attach addrserver_container"

        project.exec {
            commandLine("osascript", "-e", "tell application \"Terminal\" to do script \"$attachCommand\"")
        }
    }
}

tasks.register("runAddrServerLinux") {
    group = "docker-addressing_server"
    description = "Does the same as runAddrServerWipeImg but opens a new terminal for the container output on Linux!"

    dependsOn("safeRemoveAddrServerContainer", "safeRemoveAddrServerImage")
    dependsOn("startNewAddrServerContainer")

    doLast {
        println("Addressing Server container started from new image 'addrserver:latest'. Previous image removed from disk.")
        println("Launching a new Linux terminal.......")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")

        // Attach to the running container in a new terminal
        val attachCommand = "docker attach addrserver_container"

        // For GNOME Terminal:
        project.exec {
            commandLine("gnome-terminal", "--", "bash", "-c", attachCommand)
        }

        // If you're on KDE/XFCE/etc., you can use:
        // project.exec {
        //     commandLine("x-terminal-emulator", "-e", attachCommand)
        // }
    }
}

// >-------------------- END OF TASKS FOR OPENING NEW TERMINAL WHEN RUNNING A NEW CONTAINER ------------------<


// >>>>>>>>>>>>>>>>>>>>>>>>>>> TASKS FOR REPLICAS <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// >>>>>>>>>>>>>>>>>>>>>>>>>>> TASKS FOR REPLICAS <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// >>>>>>>>>>>>>>>>>>>>>>>>>>> TASKS FOR REPLICAS <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// >>>>>>>>>>>>>>>>>>>>>>>>>>> TASKS FOR REPLICAS <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

// Task to kill the replica container if it is running.

val replica1Container = tasks.register<DockerCreateContainer>("buildReplicaContainer") {
    group = "docker-addressing_server_replica"
    description = "Creates a Docker container for a replica using the latest addressingserver image (addrserver:latest)"

    dependsOn("safeRemoveReplica1Container", "buildAddrServerImage")
    imageId.set("addrserver:latest")
    containerName.set("replica1_container")

    // >---------------- WE CAN DEFINE PORT BINDING AND NETWORKS HERE ---------------------<
    // Handled in the docker compose file
    // withEnvVar("AS_ROLE", "BACKUP")
    // withEnvVar("HOST_ADDRESS", "0.0.0.0")
    // withEnvVar("AS_CLIENT_PORT", "49810")
    // withEnvVar("AS_REPLICA_PORT", "49811")
    // withEnvVar("AS_CHATSERVER_PORT", "49812")
    // withEnvVar("PRIMARY_HOST", "addrserver_container")
    // withEnvVar("PRIMARY_PORT", "49801")
    // // NEED TO USE DIFFERENT PORTS THAN THE PRIMARY
    // val replica1Ports = listOf("49810:49810", "49811:49811", "49812:49812")
    // // MAY WANT TO USE hostConfig.exposePorts() IN FUTURE BUILDS
    // hostConfig.portBindings.set(replica1Ports)

    // Printing the container name and image ID to console
    doLast {
        println("addressingserver replica container built - Name: ${containerName.get()}")
    }
}

tasks.register<DockerKillContainer>("killReplica1Container") {
    group = "docker-addressing_server_replica"
    description = "Kills the Replica1 container (replica1_container) if it is running."
    targetContainerId("replica1_container")
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("replica1_container").exec()
            containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'replica1_container' has been killed.")
    }
}

// Task to remove the replica container if it exists and is not running.
tasks.register<DockerRemoveContainer>("removeReplica1Container") {
    group = "docker-addressing_server_replica"
    description = "Removes the Replica1 container if it is not running."
    targetContainerId("replica1_container")
    force.set(true)
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("replica1_container").exec()
            !containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'replica1_container' has been removed.")
    }
}

// Composite task to safely remove the replica container.
tasks.register("safeRemoveReplica1Container") {
    group = "docker-addressing_server_replica"
    description = "Kills the Replica1 container if running; otherwise removes it if it exists."
    dependsOn("killReplica1Container", "removeReplica1Container")
}

// Task to start the replica container after ensuring it is freshly created.
tasks.register<DockerStartContainer>("startNewReplica1Container") {
    group = "docker-addressing_server_replica"
    description = "Builds a new Replica1 container and starts it."
    dependsOn(replica1Container)
    targetContainerId("replica1_container")
}

// Task to stream logs from the replica container.
tasks.register<DockerLogsContainer>("streamReplica1Logs") {
    group = "docker-addressing_server_replica"
    description = "Streams logs from the Replica1 container to the console."
    targetContainerId("replica1_container")
    follow.set(true)
}

// Task to fully remove and rebuild the replica container from a fresh image.
tasks.register("runReplica1WipeImg") {
    group = "docker-addressing_server_replica"
    description = "Builds and runs a Replica1 Container from a new Image - deletes the current Image and Container.\n" +
            "\t\t\t(Dockerfile -> AddrServer Image -> Replica1 Container)."
    dependsOn("safeRemoveReplica1Container")
    dependsOn("startNewReplica1Container", "streamReplica1Logs")
    doLast {
        println("Replica1 container started from new image 'addrserver:latest'. Previous image removed from disk.")
    }
}

tasks.register("runReplica1Windows") {
    group = "docker-addressing_server_replica"
    description = "Does the exact same thing as runAddrServerWipeImg but opens a new terminal for the containers output!"
    dependsOn("safeRemoveReplica1Container")
    dependsOn("startNewReplica1Container")

    doLast {
        println("Addressing Server container started from new image 'addrserver:latest'. Previous image removed from disk.")
        println("Launching a new Windows terminal.......")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")

        // Attach to the running container's shell in a new terminal
        val attachCommand = "docker attach replica1_container"
        project.exec {
            commandLine("cmd", "/c", "start", "cmd", "/k", attachCommand)
        }

    }
}


// Task to create a Docker container for the second replica.
val replica2Container = tasks.register<DockerCreateContainer>("buildReplica2Container") {
    group = "docker-addressing_server_replica"
    description = "Creates a Docker container for Replica2 using the latest addressingserver image (addrserver:latest)"

    // Remove the previous replica2 container if it exists and ensure the image is built
    dependsOn("safeRemoveReplica2Container", "buildAddrServerImage")
    imageId.set("addrserver:latest")
    containerName.set("replica2_container")

    // Configure environment variables for the second replica
    withEnvVar("AS_ROLE", "REPLICA")
    withEnvVar("HOST_ADDRESS", "0.0.0.0")
    // Use different ports than Replica1 and the primary:
    withEnvVar("AS_CLIENT_PORT", "49820")
    withEnvVar("AS_REPLICA_PORT", "49821")
    withEnvVar("AS_CHATSERVER_PORT", "49822")
    withEnvVar("PRIMARY_HOST", "addrserver_container")
    withEnvVar("PRIMARY_PORT", "49801")

    // // Define port bindings for Replica2
    // val replica2Ports = listOf("49820:49820", "49821:49821", "49822:49822")
    // hostConfig.portBindings.set(replica2Ports)

    doLast {
        println("Addressing server replica container built - Name: ${containerName.get()}")
    }
}

// Task to kill the second replica container if it is running.
tasks.register<DockerKillContainer>("killReplica2Container") {
    group = "docker-addressing_server_replica"
    description = "Kills the Replica2 container (replica2_container) if it is running."
    targetContainerId("replica2_container")
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("replica2_container").exec()
            containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'replica2_container' has been killed.")
    }
}

// Task to remove the second replica container if it exists and is not running.
tasks.register<DockerRemoveContainer>("removeReplica2Container") {
    group = "docker-addressing_server_replica"
    description = "Removes the Replica2 container if it is not running."
    targetContainerId("replica2_container")
    force.set(true)
    onlyIf {
        try {
            val containerInfo = dockerClient.inspectContainerCmd("replica2_container").exec()
            !containerInfo.state.running
        } catch (e: Exception) {
            false
        }
    }
    doLast {
        println("Container 'replica2_container' has been removed.")
    }
}

// Composite task to safely remove the second replica container.
tasks.register("safeRemoveReplica2Container") {
    group = "docker-addressing_server_replica"
    description = "Kills the Replica2 container if running; otherwise removes it if it exists."
    dependsOn("killReplica2Container", "removeReplica2Container")
}

// Task to start the second replica container.
tasks.register<DockerStartContainer>("startNewReplica2Container") {
    group = "docker-addressing_server_replica"
    description = "Builds a new Replica2 container and starts it."
    dependsOn(replica2Container)
    targetContainerId("replica2_container")
}

// Task to stream logs from the second replica container.
tasks.register<DockerLogsContainer>("streamReplica2Logs") {
    group = "docker-addressing_server_replica"
    description = "Streams logs from the Replica2 container to the console."
    targetContainerId("replica2_container")
    follow.set(true)
}

// Composite task to fully remove and rebuild the second replica container from a fresh image.
tasks.register("runReplica2WipeImg") {
    group = "docker-addressing_server_replica"
    description = "Builds and runs a Replica2 container from a new image - deletes the current image and container.\n" +
            "\t(Dockerfile -> AddrServer Image -> Replica2 Container)."
    dependsOn("safeRemoveReplica2Container")
    dependsOn("startNewReplica2Container", "streamReplica2Logs")
    doLast {
        println("Replica2 container started from new image 'addrserver:latest'. Previous image removed from disk.")
    }
}

// Task to run the second replica container on Windows, opening a new terminal for its output.
tasks.register("runReplica2Windows") {
    group = "docker-addressing_server_replica"
    description = "Runs Replica2 similar to runReplica2WipeImg but opens a new terminal for container output on Windows."
    dependsOn("safeRemoveReplica2Container")
    dependsOn("startNewReplica2Container")
    doLast {
        println("Replica2 container started from new image 'addrserver:latest'. Previous image removed from disk.")
        println("Launching a new Windows terminal for Replica2 output.......")
        println("\n>------YOU MUST HALT THE PROCESS IN THIS WINDOW MANUALLY WITH CTRL-C------<\n")
        val attachCommand = "docker attach replica2_container"
        project.exec {
            commandLine("cmd", "/c", "start", "cmd", "/k", attachCommand)
        }
    }
}
