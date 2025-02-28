// Import the DockerBuildImage task from the bmuschko plugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer


// Applying all the necessary plugins for Java application and Docker support
plugins {
    id("java") 								// Enables Java support for this Gradle module
    id("application") 						// Defines this project as an application with a main entry point
    id("com.bmuschko.docker-remote-api") 	// Adds Docker support via the bmuschko Gradle plugin. This automates Docker builds.
}

// Declaring Dependencies for the chatserver module only - keeps dependencies independant for each module
dependencies {
    implementation(project(":utilities"))  // Adds dependency on the utilities module
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2") // JSON support
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0") // JUnit 5 testing framework
}


// Configures the Java application plugin
application {
    mainClass.set("io.github.cpsc559.team16.chatserver.ChatServer") // Inform Gradle which class contains the main method for launching the app.
}

// Define the JAR task to generate chatserver.jar
tasks.jar {
    archiveFileName.set("chatserver.jar")        			// Sets the output JAR file name
    destinationDirectory.set(file("$buildDir/libs")) 	    // Specifies which directory the JAR will be placed in
}

// Task to create a fat/uber JAR (includes dependencies for standalone execution)
tasks.register<Jar>("chatServerFatJar") {
    group = "build"
    description = "Creates a runnable JAR for the Chat Server application."

    archiveFileName.set("chatserver.jar")
    destinationDirectory.set(file("$buildDir/libs"))

    from(sourceSets.main.get().output) 						// Includes compiled Java/Kotlin files

    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
}

// Task to build and package the JAR before creating the Docker image
tasks.register("buildChatServerJar") {
    group = "build"
    description = "Compiles and packages the chatserver.jar before the Docker image is built."

    dependsOn("chatServerFatJar")  // You'll notice a heirarchy of "dependsOn" function calls continuing in buildChatServerImage

    doLast {
        println("Chat Server JAR successfully created: $buildDir/libs/chatserver.jar")
    }
}


// Using the plugin to build the Chat Server Docker image
tasks.register("buildChatServerImage", DockerBuildImage::class) {
    group = "docker" 						// Categorizes this task under the "docker" group when running `gradlew tasks` to see the available tasks
    description = "Builds the Docker image for the Chat Server."
    dependsOn("buildChatServerJar") 		// Ensures the JAR is built before Docker runs (compiling everything in the container isn't ideal, especially if we want to spin up many processes)
    inputDir.set(file("."))  				// Sets the build context to the current directory (chatserver module)
    dockerFile.set(file("Dockerfile"))  	// Uses the `Dockerfile` located in this directory for building the image
    images.add("chatserver:latest") 		// Tags the built Docker image as "chatserver:latest"
}





