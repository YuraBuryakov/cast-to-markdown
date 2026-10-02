# CastToMarkdown

Java-native library that converts common document formats into clean, LLM/RAG-friendly Markdown through one consistent API, using mature Java parsers under the hood.

> **Status:** early development (`0.1.0-SNAPSHOT`). PDF → plain text split into paragraphs works; headings, lists and tables are not detected yet.

## Goals

- One small Java API for several formats.
- Runs embedded in the JVM: no Python, Docker or external service for the basic formats.
- Markdown that keeps useful structure (headings, lists, links, simple tables) and drops obvious extraction noise.
- Built on mature libraries (Apache PDFBox, Apache POI, jsoup) instead of custom parsers.

## Usage

```java
CastToMarkdown converter = CastToMarkdown.create();   // immutable, thread-safe: create once, reuse
PreparedDocument document = converter.convert(Path.of("report.pdf"));
String markdown = document.markdown();
```

Errors are unchecked: `UnsupportedFormatException` for unsupported formats, `DocumentConversionException` for unreadable or damaged files (the original exception is the cause).

## Planned formats for v0.1

PDF, DOCX, HTML, TXT, CSV.

## Project structure

A single Maven module for now.
`io.github.yuraburyakov.casttomarkdown` is the only API package. Format converters live in `internal.*` packages (one package per format), which the module descriptor does not export and which may change in any version.

The project will be split into Maven modules when a second heavy format (DOCX via Apache POI) arrives, so users who need only PDF do not pull POI.

## Build

Requires JDK 17+ and Maven 3.6.3+.

```bash
mvn verify
```

## License

[Apache License 2.0](LICENSE)
