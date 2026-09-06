plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
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

    // com.android.kotlin.multiplatform.library folds what used to be the separate top-level
    // android {} extension into this block. Tests are disabled by default under this plugin -
    // withHostTest/withDeviceTest are the explicit opt-in that used to be automatic.
    android {
        namespace = "io.github.sporadiclemon.statementparser"
        compileSdk = 37
        minSdk = 28

        // jvmToolchain(17) above already governs every target's bytecode level; the guide's
        // per-target compilerOptions.configure{} block did not resolve against this AGP/KGP
        // version's actual API surface and is redundant with the toolchain setting anyway.

        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // Appears in this library's own public signatures - LocalDate on ParsedTransaction -
            // so it must be `api`. Declared as `implementation` it would be omitted from the
            // published compile-time metadata and consumers couldn't resolve the type they're
            // handed. Coroutines and DataStore moved out with ColumnMappingStore into the
            // optional :statement-parser-datastore module - see that module's build.gradle.kts.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.pdfbox.android)
        }
        jvmMain.dependencies {
            implementation(libs.pdfbox)
        }
        // Renamed from androidInstrumentedTest: this plugin's source set naming for the
        // device-test (instrumented) side is androidDeviceTest, not the older KMP-hierarchy name.
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
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
    // Uploads and validates but does not auto-release; the deployment sits pending in the
    // Central Portal until reviewed and published there (or via publishAndReleaseToMavenCentral).
    publishToMavenCentral()
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
