object Versions {
  const val COROUTINES = "1.5.2-native-mt"
  const val DOKKA = "1.5.0"
  const val KOTLIN = "1.5.31"
  const val KOTLIN_POET = "1.9.0"
  const val KOTLIN_COMPILE_TESTING = "1.4.4"
  const val KOTLINX_SERIALIZATION = "1.3.0-RC"
  const val KSP = "$KOTLIN-1.0.0"
  const val KTLINT = "0.40.0"
  const val KTLINT_GRADLE_PLUGIN = "10.1.0"
  const val KTOR = "2.0.0-eap-213"
  const val MAVEN_PUBLISH_PLUGIN = "0.17.0"
}

object Dependencies {
  object KotlinX {
    object Coroutines {
      const val CORE = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}"
      const val TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES}"
    }

    object Serialization {
      const val CORE = "org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.KOTLINX_SERIALIZATION}"
      const val JSON = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.KOTLINX_SERIALIZATION}"
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
