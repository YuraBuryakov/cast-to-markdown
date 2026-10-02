# CastToMarkdown

Java-native library that converts common document formats into clean, LLM/RAG-friendly Markdown through one consistent API, using mature Java parsers under the hood.

> **Status:** early development (`0.1.0-SNAPSHOT`), not published to Maven Central yet. PDF: paragraphs, headings, bullet lists, headers/footers removed (no tables yet). DOCX: headings, lists, tables, footnotes.

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

From a stream, for example a Spring `MultipartFile` upload. The stream is read but not closed: you own it.

```java
try (InputStream in = upload.getInputStream()) {
    String markdown = converter.convert(in, upload.getOriginalFilename()).markdown();
}
```

Settings: `create()` uses the defaults, `builder()` changes them. Documents larger than the limit (100 MiB by default) are rejected with `DocumentTooLargeException` before parsing; a stream is read only up to the limit. The whole document is in memory while it is converted, so with parallel calls plan for about *limit × threads*.

```java
CastToMarkdown converter = CastToMarkdown.builder()
        .maxDocumentSize(20 * 1024 * 1024)   // 20 MiB
        .build();
```

DOCX: headings come from the paragraph styles (`Title`, `Heading 1..6`); bold text without a heading style stays a paragraph. Lists keep their nesting and numbering, tables become Markdown tables, footnotes become Markdown footnotes; running headers and footers are left out. Apache POI logs through Log4j API: without a Log4j provider (or the `log4j-to-slf4j` bridge that Spring Boot includes) it prints one `Log4j API could not find a logging provider` line to stderr.

Errors are unchecked: `UnsupportedFormatException` for unsupported formats, `DocumentConversionException` for unreadable or damaged files (the original exception is the cause).

Scanned PDFs (pages are images without a text layer) are not supported: the converter throws `UnsupportedFormatException` instead of returning empty Markdown. Run OCR first, for example with [OCRmyPDF](https://ocrmypdf.readthedocs.io/), which adds a text layer to the PDF; the result can then be converted.

## Planned formats for v0.1

PDF, DOCX, HTML, TXT, CSV.

## Project structure

| Module | Artifact | Content |
|---|---|---|
| `core` | `cast-to-markdown-core` | the API (`io.github.yuraburyakov.casttomarkdown`); no parser dependencies |
| `pdf` | `cast-to-markdown-pdf` | PDF, based on Apache PDFBox |
| `docx` | `cast-to-markdown-docx` | DOCX (Word 2007+), based on Apache POI |

Add the format modules you need; they depend on `core` and are found automatically (`ServiceLoader`), so users who need only PDF do not pull other parsers. `io.github.yuraburyakov.casttomarkdown` is the only API package; the converter contract in `internal` is exported only to the format modules and may change in any version.

## Build

Requires JDK 17+ and Maven 3.6.3+.

```bash
mvn verify
```

## License

[Apache License 2.0](LICENSE)
