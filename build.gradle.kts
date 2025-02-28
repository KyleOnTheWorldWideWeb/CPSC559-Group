// ✅ Declare plugins at the root level but do not apply them globally
plugins {
    id("com.bmuschko.docker-remote-api") version "9.4.0" apply false // Each module applies it as needed
}

subprojects {
    repositories {
        mavenCentral() // Ensures dependencies are pulled from Maven Central
    }

    // ✅ Only apply this to Java-based subprojects
    plugins.withId("java") {
        dependencies {
            add("implementation", "com.fasterxml.jackson.core:jackson-databind:2.18.2") // JSON support
            add("testImplementation", "org.junit.jupiter:junit-jupiter:5.12.0") // JUnit 5
        }
    }
}
