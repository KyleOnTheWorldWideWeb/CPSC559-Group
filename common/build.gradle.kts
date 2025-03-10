plugins {
    id("java-library") // Allows other modules (client,chatserver, etc.) to consume this as a dependency
}

dependencies {
    // Add any dependencies needed for utilities
}

// Ensure a valid JAR is created
tasks.withType<Jar> {
    archiveBaseName.set("common") // Set JAR name
}
