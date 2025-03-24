plugins {
    id("java")
    id("application")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":addressingserver"))
    implementation(project(":client"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
}

application {
    mainClass.set("io.github.cpsc559.team16.chatserver.ChatServer")
}

tasks.register<Jar>("chatserverFatJar") {
    group = "build"
    archiveFileName.set("chatserver.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.chatserver.ChatServer")
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
}

tasks.register("buildChatServer") {
    group = "build"
    description = "Builds the chatserver with all dependencies (common, client, addressingserver)."
    dependsOn(":common:commonJar", ":client:clientFatJar", "chatserverFatJar")
}
// Reminder. All ports, environment variables can be assigned in a docker file or docker compose file.
