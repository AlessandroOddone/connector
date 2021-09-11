rootProject.name = "connector"

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

include(":connector-codegen")
include(":connector-processor")
include(":connector-runtime-core")
include(":connector-runtime-http")
include(":e2e-tests")
include(":test-util")
