plugins {
    id("java")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.json:json:20231013") 
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
}


tasks.register<Jar>("commonJar") {
    group = "build"
    archiveFileName.set("common.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
}
