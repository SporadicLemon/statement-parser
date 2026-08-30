plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

group = "io.github.sporadiclemon"
version = libs.versions.statementParser.get()

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // These three appear in this library's own public signatures — LocalDate on
            // ParsedTransaction, and DataStore/Flow on ColumnMappingStore — so they must be
            // `api`. Declared as `implementation` they are omitted from the published
            // compile-time metadata and consumers cannot resolve the types they are handed.
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.datastore.preferences.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.pdfbox.android)
        }
        jvmMain.dependencies {
            implementation(libs.pdfbox)
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

android {
    namespace = "io.github.sporadiclemon.statementparser"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

mavenPublishing {
    if (providers.gradleProperty("signingKey").isPresent ||
        System.getenv("ORG_GRADLE_PROJECT_signingKey") != null
    ) {
        signAllPublications()
    }
    // Removed coordinates() call to avoid "final and cannot be changed" error
    pom {
        name.set("statement-parser")
        description.set("Kotlin Multiplatform library for parsing CSV and OFX/QFX bank statements on-device.")
        url.set("https://github.com/sporadiclemon/statement-parser")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://opensource.org/licenses/Apache-2.0")
            }
        }
        developers {
            developer {
                id.set("sporadiclemon")
                name.set("Paul Mitchell")
            }
        }
        scm {
            url.set("https://github.com/sporadiclemon/statement-parser")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sporadiclemon/statement-parser")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR") ?: "")
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
            }
        }
    }
}
