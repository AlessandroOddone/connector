import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
  kotlin("multiplatform") version Versions.KOTLIN apply false
  kotlin("jvm") version Versions.KOTLIN apply false
  kotlin("plugin.serialization") version Versions.KOTLIN apply false
  id("com.google.devtools.ksp") version Versions.KSP apply false
  id("org.jlleitschuh.gradle.ktlint") version Versions.KTLINT_GRADLE_PLUGIN apply false
  id("com.vanniktech.maven.publish") version Versions.MAVEN_PUBLISH_PLUGIN apply false
  id("org.jetbrains.dokka") version Versions.DOKKA apply false
  id("signing")
}

subprojects {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
    maven { url = uri("https://kotlin.bintray.com/kotlinx/") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
    jcenter()
    mavenLocal()
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions {
      freeCompilerArgs = freeCompilerArgs + listOf(
        "-Xopt-in=kotlin.RequiresOptIn",
        "-Xopt-in=kotlin.contracts.ExperimentalContracts",
        "-Xopt-in=io.ktor.util.KtorExperimentalAPI"
      )
    }
  }

  tasks.withType<JavaCompile> {
    sourceCompatibility = "8"
    targetCompatibility = "8"
  }

  tasks.withType<Test> {
    maxHeapSize = "4g"
    maxParallelForks = 1
  }

  val isJvmOnly = isJvmOnly()
  if (isJvmOnly) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
  } else {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
  }

  configure<KotlinProjectExtension> {
    explicitApi()
  }

  if (!isJvmOnly) {
    configure<KotlinMultiplatformExtension> {
      jvm()
      /* TODO enable other targets as KSP adds support for them
      js {
        browser {
        }
        nodejs {
        }
      }
      macosX64()
      ios()
      watchos()
      tvos()
      linuxX64()
      linuxArm32Hfp()
      linuxMips32()
      androidNativeArm32()
      androidNativeArm64()
      mingwX64()
      */

      sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
      }
    }
  }

  if (!isInternal()) {
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "org.jetbrains.dokka")
  }
}

subprojects {
  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  configure<KtlintExtension> {
    enableExperimentalRules.set(true)
  }
}

signing {
  if (project.hasProperty("SIGNING_PRIVATE_KEY") && project.hasProperty("SIGNING_PASSWORD")) {
    useInMemoryPgpKeys(
      project.property("SIGNING_PRIVATE_KEY").toString(),
      project.property("SIGNING_PASSWORD").toString()
    )
  }
}

fun Project.isJvmOnly(): Boolean {
  return name.endsWith("codegen") ||
    name.endsWith("processor") ||
    name.endsWith("integration-tests")
}

fun Project.isInternal(): Boolean {
  return name.startsWith("test") || name.endsWith("tests")
}
