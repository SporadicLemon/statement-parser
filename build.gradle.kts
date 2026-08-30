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

    compilerOptions {
        // expect/actual classes are still flagged Beta; the warning is noise on every compile.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
        getByName("androidInstrumentedTest") {
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
    // The vanniktech plugin signs with an in-memory PGP key read from the properties
    // signingInMemoryKey / signingInMemoryKeyId / signingInMemoryKeyPassword - not from a
    // property named "signingKey". Gating on the wrong name meant signAllPublications() ran
    // with no key configured, which fails every signing task rather than skipping them.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
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
            connection.set("scm:git:https://github.com/sporadiclemon/statement-parser.git")
            developerConnection.set("scm:git:ssh://git@github.com/sporadiclemon/statement-parser.git")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sporadiclemon/statement-parser")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).getOrElse("")
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).getOrElse("")
            }
        }
    }
}
