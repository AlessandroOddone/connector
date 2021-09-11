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

allprojects {
  repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    mavenLocal()
  }
}

subprojects {
  tasks.withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "1.8"
      allWarningsAsErrors = true
      freeCompilerArgs = freeCompilerArgs + listOf(
        "-Xopt-in=kotlin.RequiresOptIn",
        "-Xopt-in=kotlin.contracts.ExperimentalContracts"
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
      js {
        browser()
        nodejs()
      }
      val nativeTargets = listOf(
        // Apple
        iosArm64(),
        iosX64(),
        macosX64(),
        tvosArm64(),
        tvosX64(),
        watchosArm32(),
        watchosArm64(),
        watchosX86(),
        // Linux
        linuxX64(),
        // Windows
        mingwX64(),
      )

      sourceSets {
        val commonMain by getting
        val commonTest by getting
        val nativeMain by creating {
          dependsOn(commonMain)
        }
        val nativeTest by creating {
          dependsOn(commonTest)
        }
        for (nativeTarget in nativeTargets) {
          getByName("${nativeTarget.name}Main") {
            dependsOn(nativeMain)
          }
          getByName("${nativeTarget.name}Test") {
            dependsOn(nativeTest)
          }
        }

        all {
          languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
        }
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
    version.set(Versions.KTLINT)
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
  return name.endsWith("codegen") || name.endsWith("processor")
}

fun Project.isInternal(): Boolean {
  return name.startsWith("test") || name.endsWith("tests")
}
