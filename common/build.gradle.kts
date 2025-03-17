plugins {
    id("java-library") // Allows other modules (client, chatserver, etc.) to consume this as a dependency
}

dependencies {
    // Add any dependencies needed for utilities
    implementation("org.json:json:20240303") // Add org.json dependency
}

// Ensure a valid JAR is created
tasks.withType<Jar> {
    archiveBaseName.set("common") // Set JAR name
}
