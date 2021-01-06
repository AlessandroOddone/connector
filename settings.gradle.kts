rootProject.name = "connector"

enableFeaturePreview("GRADLE_METADATA")

pluginManagement {
  resolutionStrategy {
    eachPlugin {
      when (requested.id.id) {
        "symbol-processing" -> useModule("com.google.devtools.ksp:symbol-processing:${requested.version}")
      }
    }
  }

  repositories {
    gradlePluginPortal()
    google()
    jcenter()
    mavenCentral()
  }
}

include(":connector-codegen")
include(":connector-processor")
include(":connector-runtime-core")
include(":connector-runtime-http")
include(":integration-tests")
include(":test-util")
