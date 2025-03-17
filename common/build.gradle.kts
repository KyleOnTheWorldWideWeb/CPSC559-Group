plugins {
    id("java-library") // Allows other modules (client, chatserver, etc.) to consume this as a dependency
}

dependencies {
    // Add any dependencies needed for utilities
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

tasks.withType<Jar> {
    archiveBaseName.set("common") // Set JAR name
    from(sourceSets.main.get().output) // Ensure all classes are included in the JAR
}