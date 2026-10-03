# CastToMarkdown

Java-native library that converts common document formats into clean, LLM/RAG-friendly Markdown through one consistent API, using mature Java parsers under the hood.

> **Status:** early development (`0.1.0-SNAPSHOT`), preparing the first release `0.1.0`; the API may still change before `1.0`. PDF: paragraphs, headings, bullet lists, headers/footers removed, tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice exports), web links. DOCX: headings, lists, tables, footnotes, links.

## Goals

- One small Java API for several formats.
- Runs embedded in the JVM: no Python, Docker or external service for the basic formats.
- Markdown that keeps useful structure (headings, lists, links, simple tables) and drops obvious extraction noise.
- Built on mature libraries (Apache PDFBox, Apache POI, jsoup) instead of custom parsers.

## Installation

After the first release, add `core` and the format modules you need (Java 17+):

```xml
<dependency>
    <groupId>io.github.yuraburyakov</groupId>
    <artifactId>cast-to-markdown-pdf</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.yuraburyakov</groupId>
    <artifactId>cast-to-markdown-docx</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle: `implementation("io.github.yuraburyakov:cast-to-markdown-pdf:0.1.0")`. Each format module brings `cast-to-markdown-core` with it. It works on the class path and on the module path (module `io.github.yuraburyakov.casttomarkdown`).

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

Settings: `create()` uses the defaults, `builder()` changes them. Documents larger than the limit (100 MiB by default) are rejected with `DocumentTooLargeException` before parsing; a stream is read only up to the limit. The limit is on the source size, not on memory: parsers build an object model of the document, so the heap a conversion needs depends on the format and content and may be many times the file size (a 1.7 MB DOCX with 7.6 MB of text XML needed about 100 MB). A file is read from disk as needed; a stream is read into memory in full first.

```java
CastToMarkdown converter = CastToMarkdown.builder()
        .maxDocumentSize(20 * 1024 * 1024)   // 20 MiB
        .build();
```

DOCX: headings come from the paragraph styles (`Title`, `Heading 1..6`); bold text without a heading style stays a paragraph. Lists keep their nesting and numbering, tables become Markdown tables, footnotes become Markdown footnotes; running headers and footers are left out. Apache POI logs through Log4j API: without a Log4j provider (or the `log4j-to-slf4j` bridge that Spring Boot includes) it prints one `Log4j API could not find a logging provider` line to stderr.

Errors are unchecked: `UnsupportedFormatException` for unsupported formats, `DocumentConversionException` for unreadable or damaged files (the original exception is the cause).

Scanned PDFs (pages are images without a text layer) are not supported: the converter throws `UnsupportedFormatException` instead of returning empty Markdown. Run OCR first, for example with [OCRmyPDF](https://ocrmypdf.readthedocs.io/), which adds a text layer to the PDF; the result can then be converted.

## Formats

| Format | Version | Module |
|---|---|---|
| PDF (with a text layer) | 0.1 | `cast-to-markdown-pdf` |
| DOCX (Word 2007+) | 0.1 | `cast-to-markdown-docx` |
| HTML, TXT, CSV | planned | |

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
