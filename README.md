# statement-parser

Kotlin Multiplatform library for parsing CSV, OFX/QFX, and PDF bank statements on-device.

## Features

- **Multiplatform:** Supports Android, iOS, JVM (Desktop/Server).
- **Format Support:** Automatic detection and parsing of CSV, OFX/QFX, and PDF.
- **Bank Profiles:** Built-in profiles for major UK banks - see [Supported Banks](#supported-banks).
- **Custom Mapping:** Flexibly map any CSV format to a standard transaction model.
- **On-device:** All parsing happens locally; no financial data ever leaves the device.

## Supported Banks

| Bank | CSV | PDF |
|------|:---:|:---:|
| Monzo | ✅ | ✅ |
| Starling | ✅ | ✅ |
| NatWest | ✅ | ✅ |
| HSBC | ✅ | ✅ (current account and credit card) |
| American Express | — | ✅ |
| Barclays | ✅ | — |
| Lloyds | ✅ | — |
| Santander | ✅ | — |

CSV detection matches on column headers (see [`CsvBankProfiles`](src/commonMain/kotlin/io/github/sporadiclemon/statementparser/CsvBankProfiles.kt));
PDF detection matches on first-page text - see [Supported PDF Banks](#supported-pdf-banks) below
for exactly what each one looks for.

> **On logos:** this table intentionally doesn't embed bank logos. They're trademarked (and
> usually copyrighted) brand assets, and this project has no affiliation with any bank listed
> here - hosting their marks alongside "supported" could read as an implied partnership that
> doesn't exist. If you'd like logos here, the safe route is sourcing them yourself from each
> bank's own press/media kit under that bank's brand guidelines.

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
    implementation("io.github.sporadiclemon:statement-parser:0.1.0")
}
```

### Optional: persisting a custom ColumnMapping

If you let a user confirm a `ColumnMapping` for a CSV export from an unrecognised bank and
want to remember it, add the separate `statement-parser-datastore` module:

```kotlin
dependencies {
    implementation("io.github.sporadiclemon:statement-parser:0.1.0")
    implementation("io.github.sporadiclemon:statement-parser-datastore:0.1.0")
}
```

This is a separate artifact - not a transitive dependency of the core module - so an app that
never needs to remember a custom mapping does not pull in AndroidX DataStore, okio, or
coroutines just to use `StatementParser`.

```kotlin
val store = ColumnMappingStore(dataStore) // your app's DataStore<Preferences>
store.save("MyBank", mapping)
val remembered: ColumnMapping? = store.get("MyBank").first()
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
val result = parser.parsePdf(bytes)                                    // auto-detects bank
val result = parser.parsePdf(bytes, hintProfile = PdfBankProfiles.MONZO) // skip auto-detection

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
| JVM      | ✓ (Apache PDFBox) |

## License

Apache License 2.0
