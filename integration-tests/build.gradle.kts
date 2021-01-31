plugins {
  id("com.google.devtools.ksp")
  kotlin("plugin.serialization")
}

dependencies {
  ksp(project(":connector-processor"))
  testImplementation(project(":connector-processor"))
  testImplementation(project(":connector-runtime-http"))
  testImplementation(project(":test-util"))
  testImplementation(Dependencies.KOTLIN_COMPILE_TESTING)
  testImplementation(Dependencies.KSP)
  testImplementation(Dependencies.Ktor.CLIENT_LOGGING)
  testImplementation(Dependencies.Ktor.CLIENT_MOCK)
  testImplementation(Dependencies.KotlinX.Serialization.JSON_JVM)
}

sourceSets {
  test {
    java {
      // Needed until this issue is fully solved: https://github.com/android/kotlin/issues/7
      srcDir(file("build/generated/ksp/test/kotlin"))
    }
  }
}
