plugins {
    id("java")
    id("com.bmuschko.docker-remote-api") version "9.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

// Fix: Use only the `docker/chatserver/` directory as input
tasks.register<com.bmuschko.gradle.docker.tasks.image.DockerBuildImage>("buildClientImage") {
    doNotTrackState("Gradle should not track inputDir due to Docker context") //
    inputDir.set(layout.projectDirectory.dir("docker/client")) //
    dockerFile.set(file("docker/client/Dockerfile"))
    images.add("cpsc559/team16-client:latest")
}
