# statement-parser

Kotlin Multiplatform library for parsing CSV and OFX/QFX bank statements on-device.

## Features

- **Multiplatform:** Supports Android, iOS, JVM (Desktop/Server).
- **Format Support:** Automatic detection and parsing of CSV and OFX/QFX.
- **Bank Profiles:** Built-in profiles for major UK banks (Monzo, Starling, Lloyds, etc.).
- **Custom Mapping:** Flexibly map any CSV format to a standard transaction model.
- **On-device:** All parsing happens locally; no financial data ever leaves the device.

## Installation

### GitHub Packages

Add the repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/sporadiclemon/statement-parser")
            credentials {
                username = "your-github-username"
                password = "your-github-token"
            }
        }
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("io.github.sporadiclemon:statement-parser:0.0.1")
}
```

## Usage

```kotlin
val parser = StatementParser()
val content = file.readText()
val format = parser.detectFormat(file.name, content)

val result = parser.parse(content, format).getOrThrow()
result.transactions.forEach { 
    println("${it.date}: ${it.description} (${it.amount})")
}
```

## License

Apache License 2.0
