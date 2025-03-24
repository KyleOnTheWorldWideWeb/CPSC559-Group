plugins {
    id("java")
    id("application")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

dependencies {
    implementation(project(":common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
}

application {
    mainClass.set("io.github.cpsc559.team16.addressingserver.AddressingServer")
}

tasks.register<Jar>("addressingserverFatJar") {
    group = "build"
    archiveFileName.set("addressingserver.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes("Main-Class" to "io.github.cpsc559.team16.addressingserver.AddressingServer")
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("buildAddressingServer") {
    group = "build"
    description = "Builds the addressing server with common library dependencies."
    dependsOn(":common:commonJar", "addressingserverFatJar")
}