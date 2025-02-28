// Import the DockerBuildImage task from the bmuschko plugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

// Applying all the necessary plugins for Java application and Docker support
plugins {
    id("java")                               // Enables Java support for this Gradle module
    id("application")                        // Defines this project as an application with a main entry point
    id("com.bmuschko.docker-remote-api")     // Adds Docker support via the bmuschko Gradle plugin. This automates Docker builds.
}

// Configures the Java application plugin
application {
    mainClass.set("io.github.cpsc559.team16.addressingserver.AddressingServer") // Inform Gradle which class contains the main method for launching the app.
}


// Define a custom Gradle task to build the Addressing Server Docker image
tasks.create("buildAddressingServerImage", DockerBuildImage::class) {
    group = "docker"                          // Categorizes this task under the "docker" group when running `gradlew tasks` to see the available tasks
    description = "Builds the Docker image for the Addressing Server."

    dependsOn("jar")                          // Ensures the JAR is built before Docker runs (compiling everything in the container isn't ideal, especially if we want to spin up many processes)

    inputDir.set(file("."))                   // Sets the build context to the current directory (addressingserver module)
    dockerFile.set(file("Dockerfile"))        // Uses the `Dockerfile` located in this directory for building the image
    images.add("addressingserver:latest")     // Tags the built Docker image as "addressingserver:latest"
}

