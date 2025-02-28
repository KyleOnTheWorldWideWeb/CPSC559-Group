// Import the DockerBuildImage, DockerCreateContainer tasks from the bmuschko plugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer

// Applying all the necessary plugins for Java application and Docker support
plugins {
    id("java")                               // Enables Java support for this Gradle module
    id("application")                        // Defines this project as an application with a main entry point
    id("com.bmuschko.docker-remote-api")     // Adds Docker support via the bmuschko Gradle plugin. This automates Docker builds.
}

// Configures the Java application plugin
application {
    mainClass.set("io.github.cpsc559.team16.client.Client") // Inform Gradle which class contains the main method for launching the app.
}

// Define the JAR task to generate client.jar
tasks.jar {
    archiveFileName.set("client.jar")        			// Sets the output JAR file name
    destinationDirectory.set(file("$buildDir/libs")) 	// Specifies which directory the JAR will be placed in
}

// Task to create a fat/uber JAR (includes dependencies for standalone execution)
tasks.register<Jar>("clientFatJar") {
    group = "build"
    description = "Creates a runnable JAR for the Client application."

    archiveFileName.set("client.jar")  						
    destinationDirectory.set(file("$buildDir/libs")) 		

    from(sourceSets.main.get().output) 						// Includes compiled Java/Kotlin files

    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
}

// Task to build and package the JAR before creating the Docker image
tasks.register("buildClientJar") {
    group = "build"
    description = "Compiles and packages the client.jar before the Docker image is built."

    dependsOn("clientFatJar") 

    doLast {
        println("Client JAR successfully created: $buildDir/libs/client.jar")
    }
}

// Define a custom Gradle task to build the Client Docker image
tasks.register<DockerBuildImage>("buildClientImage") {
    group = "docker"                          // Categorizes this task under the "docker" group when running `gradlew tasks` to see available tasks
    description = "Builds the Docker image for the client application."

    dependsOn("buildClientJar")                // Ensures the JAR is built before Docker runs (compiling everything in the container isn't ideal, especially if we want to spin up many processes)

    inputDir.set(file("."))			            // Sets the build context to the current directory (client module)
    dockerFile.set(file("Dockerfile"))         // Uses the `Dockerfile` located in this directory for building the image
    images.add("client:latest")                // Tags the built Docker image as "client:latest"
}
