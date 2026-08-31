// Optional add-on: DataStore-backed persistence for a ColumnMapping the user has already
// confirmed for an unrecognised bank's CSV. Split out from the core statement-parser module so
// that a consumer who never touches this - the large majority, since most statements are
// auto-detected - does not pull DataStore, okio, and coroutines into their app for a class they
// never call.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.sporadiclemon"
version = libs.versions.statementParser.get()

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // com.android.kotlin.multiplatform.library folds what used to be the separate top-level
    // android {} extension into this block. No instrumented tests here, so only withHostTest is
    // needed to keep commonTest running as this module's Android unit tests.
    android {
        namespace = "io.github.sporadiclemon.statementparser.datastore"
        compileSdk = 37
        minSdk = 28

        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // ColumnMapping, from the core module, appears in this module's own public
            // signatures (ColumnMappingStore.save/get) - api, not implementation, so it resolves
            // for a consumer of the published artifact. Coroutines and DataStore are api for the
            // same reason: Flow and DataStore<Preferences> are part of this module's public API.
            api(project(":"))
            api(libs.kotlinx.coroutines.core)
            api(libs.datastore.preferences.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
    ) {
        signAllPublications()
    }
    // Uploads and validates but does not auto-release; the deployment sits pending in the
    // Central Portal until reviewed and published there (or via publishAndReleaseToMavenCentral).
    publishToMavenCentral()
    pom {
        name.set("statement-parser-datastore")
        description.set("Optional DataStore-backed persistence for statement-parser's ColumnMapping.")
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
