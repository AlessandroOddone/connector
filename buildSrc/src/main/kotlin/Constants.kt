const val GROUP = "dev.aoddon"
const val VERSION = "0.1-SNAPSHOT"

object Versions {
  const val COROUTINES = "1.4.2"
  const val KOTLIN = "1.4.21"
  const val KOTLIN_POET = "1.7.2"
  const val KOTLIN_COMPILE_TESTING = "1.3.4"
  const val KOTLINX_SERIALIZATION = "1.0.1"
  const val KSP = "1.4.20-dev-experimental-20210203"
  const val KTLINT_GRADLE_PLUGIN = "9.4.1"
  const val KTOR = "1.4.1"
}

object Dependencies {
  object KotlinX {
    object Coroutines {
      const val CORE = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}"
      const val TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES}"
    }

    object Serialization {
      const val CORE = "org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.KOTLINX_SERIALIZATION}"
      const val JSON_JVM = "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:${Versions.KOTLINX_SERIALIZATION}"
    }
  }

  object Ktor {
    const val CLIENT_CORE = "io.ktor:ktor-client-core:${Versions.KTOR}"
    const val CLIENT_LOGGING = "io.ktor:ktor-client-logging:${Versions.KTOR}"
    const val CLIENT_MOCK = "io.ktor:ktor-client-mock:${Versions.KTOR}"
  }

  const val KOTLIN_POET = "com.squareup:kotlinpoet:${Versions.KOTLIN_POET}"
  const val KOTLIN_COMPILE_TESTING =
    "com.github.tschuchortdev:kotlin-compile-testing-ksp:${Versions.KOTLIN_COMPILE_TESTING}"
  const val KSP = "com.google.devtools.ksp:symbol-processing-api:${Versions.KSP}"
  const val KTLINT_GRADLE_PLUGIN = "org.jlleitschuh.gradle:ktlint-gradle:${Versions.KTLINT_GRADLE_PLUGIN}"
}
