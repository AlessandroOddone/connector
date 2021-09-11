kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(kotlin("test-common"))
        api(kotlin("test-annotations-common"))
        implementation(Dependencies.KotlinX.Coroutines.CORE)
      }
    }
    jvmMain {
      dependencies {
        api(kotlin("test-junit"))
        api(Dependencies.KotlinX.Coroutines.TEST)
      }
    }
    jsMain {
      dependencies {
        api(kotlin("test-js"))
      }
    }
  }
}
