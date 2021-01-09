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
}

subprojects {
  group = GROUP
  version = VERSION

  repositories {
    gradlePluginPortal()
    jcenter()
    google()
    maven { url = uri("https://kotlin.bintray.com/kotlinx/") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
    mavenCentral()
    mavenLocal()
  }

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions {
      freeCompilerArgs = freeCompilerArgs + listOf(
        "-Xopt-in=kotlin.RequiresOptIn",
        "-Xopt-in=kotlin.contracts.ExperimentalContracts"
      )
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "8"
    targetCompatibility = "8"
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
    apply(plugin = "maven-publish")
    configure<PublishingExtension> {
      publications {
        register<MavenPublication>("maven") {
          groupId = GROUP
          artifactId = project.name
          version = VERSION
        }
      }
    }
  }
}

subprojects {
  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  configure<KtlintExtension> {
    enableExperimentalRules.set(true)
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
