plugins {
    id("java")
    id("application")
}

dependencies {
    implementation(project(":common"))                                   // Adds dependency on the utilities module
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")    // JSON support
    implementation("org.jline:jline:3.21.0")                                // JLine for console input/output
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")            // JUnit 5 testing framework
    implementation("org.json:json:20231013") 

}

application {
    mainClass.set("io.github.cpsc559.team16.client.Client")
}

tasks.register<Jar>("clientFatJar") {
    group = "build"
    archiveFileName.set("client.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.client.Client")
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
}

tasks.register("buildClient") {
    group = "build"
    description = "Builds the client3 server with common library dependencies."
    dependsOn(":common:commonJar", "clientFatJar")
}