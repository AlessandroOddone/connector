dependencies {
  implementation(Dependencies.Ktor.CLIENT_CORE)
  implementation(project(":connector-runtime-http"))
  api(Dependencies.KOTLIN_POET)
}
