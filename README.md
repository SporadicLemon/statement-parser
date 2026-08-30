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

## PDF Parsing

```kotlin
val parser = StatementParser()

// Detect by filename
val format = parser.detectFormat("statement.pdf", "")
// format == StatementFormat.PDF

// Parse a PDF statement
val bytes = file.readBytes()
val result = parser.parsePdf(bytes)                          // auto-detects bank
val result = parser.parsePdf(bytes, bankHint = "Monzo")     // skip auto-detection

result.getOrThrow().transactions.forEach {
    println("${it.date}: ${it.description} (${it.amount})")
}
```

### Supported PDF Banks

| Bank             | Detected by (first page text)                  |
|------------------|------------------------------------------------|
| NatWest          | "NatWest", "National Westminster"              |
| Monzo            | "Monzo"                                        |
| HSBC             | "HSBC" (current account), "Visa Card statement" (credit card) |
| Starling         | "www.starlingbank.com", "Starling Bank Limited" |
| American Express | "American Express"                             |

Starling is matched on its page furniture rather than a bare "Starling", because the
word turns up inside payee names on other banks' statements.

### Android Setup

PdfBox-Android requires one-time initialisation. In your `Application` class:

```kotlin
override fun onCreate() {
    super.onCreate()
    PDFBoxResourceLoader.init(applicationContext)
}
```

### Platform Support

| Platform | PDF Support |
|----------|------------|
| Android  | ✓ (PdfBox-Android) |
| iOS      | ✓ (PDFKit, iOS 11+) |
| JVM      | Not yet supported |

## License

Apache License 2.0
