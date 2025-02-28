rootProject.name = "CPSC559-Group"

/*
    Registers all subprojects (modules) so that Gradle knows they exist.
    It links modules together, allowing dependencies between them (e.g. chatserver depends on utilities).
    Without it, Gradle will not recognize any subprojects, and you'll get errors when trying to reference project(":utilities").
 */
include("client", "chatserver", "addressingserver", "utilities")
