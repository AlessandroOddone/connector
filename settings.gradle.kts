rootProject.name = "connector"

enableFeaturePreview("GRADLE_METADATA")

pluginManagement {
  resolutionStrategy {
    eachPlugin {
      when (requested.id.id) {
        "kotlin-ksp", "org.jetbrains.kotlin.kotlin-ksp", "org.jetbrains.kotlin.ksp" -> {
          useModule("org.jetbrains.kotlin:kotlin-ksp:${requested.version}")
        }
      }
    }
  }

  repositories {
    gradlePluginPortal()
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    google()
    mavenCentral()
  }
}

include(":connector-codegen")
include(":connector-processor")
include(":connector-runtime-core")
include(":connector-runtime-http")
include(":integration-tests")
