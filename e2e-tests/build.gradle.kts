plugins {
  id("com.google.devtools.ksp")
  kotlin("plugin.serialization")
}

dependencies {
  ksp(project(":connector-processor"))
}

kotlin.sourceSets {
  commonTest {
    dependencies {
      implementation(project(":connector-runtime-http"))
      implementation(project(":test-util"))
      implementation(Dependencies.Ktor.CLIENT_LOGGING)
      implementation(Dependencies.Ktor.CLIENT_MOCK)
      implementation(Dependencies.KotlinX.Serialization.JSON)
    }
  }

  jvmTest {
    dependencies {
      implementation(project(":connector-processor"))
      implementation(Dependencies.KOTLIN_COMPILE_TESTING)
    }
  }

  all {
    languageSettings {
      optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }
  }
}
