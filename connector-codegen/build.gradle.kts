dependencies {
  implementation(Dependencies.Ktor.CLIENT_CORE_JVM)
  implementation(project(":connector-runtime-http"))
  api(Dependencies.KOTLIN_POET)
}
