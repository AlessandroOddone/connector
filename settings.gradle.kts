rootProject.name = "segment"

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

include(
    ":segment-codegen",
    ":segment-processor",
    ":segment-http",
    ":segment-runtime-core",
    ":segment-runtime-http",
    ":sample-jvm"
)
