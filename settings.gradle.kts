rootProject.name = "CPSC559-Group"

/*
    Registers all subprojects (modules) so that Gradle knows they exist.
    It links modules together, allowing dependencies between them (e.g. chatserver depends on utilities).
    Without it, Gradle will not recognize any subprojects, and you'll get errors when trying to reference project(":utilities").
 */
include(":client", ":chatserver", ":addressingserver", ":utilities")
/*
 NOTE: in Gradle, the colon (:) prefix is used to reference a module in a multi-module project.
 The suffix should be the absolute path to the module, relative to the root directory of the project.
 */

