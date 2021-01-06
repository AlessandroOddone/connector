kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(kotlin("test-common"))
        api(kotlin("test-annotations-common"))
        api(Dependencies.KotlinX.Coroutines.TEST)
      }
    }
    jvmMain {
      dependencies {
        api(kotlin("test-junit"))
      }
    }
  }
}
