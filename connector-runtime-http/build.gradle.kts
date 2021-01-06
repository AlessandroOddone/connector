kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(project(":connector-runtime-core"))
        api(Dependencies.Ktor.CLIENT_CORE)
        api(Dependencies.KotlinX.Serialization.CORE)
      }
    }
    commonTest {
      dependencies {
        implementation(project(":test-util"))
        implementation(Dependencies.Ktor.CLIENT_MOCK)
      }
    }
  }
}
