# CastToMarkdown

Java-native library that converts common document formats into clean, LLM/RAG-friendly Markdown through one consistent API, using mature Java parsers under the hood.

> **Status:** early development (`0.1.0-SNAPSHOT`), preparing the first release `0.1.0`; the API may still change before `1.0`. PDF: paragraphs, headings, bullet lists, headers/footers removed, tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice exports), web links. DOCX: headings, lists, tables, footnotes, links.

## Goals

- One small Java API for several formats.
- Runs embedded in the JVM: no Python, Docker or external service for the basic formats.
- Markdown that keeps useful structure (headings, lists, links, simple tables) and drops obvious extraction noise.
- Built on mature libraries (Apache PDFBox, Apache POI, jsoup) instead of custom parsers.

## Installation

After the first release, add the format modules you need (Java 17+); each brings `cast-to-markdown-core` with it:

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

Gradle: `implementation("io.github.yuraburyakov:cast-to-markdown-pdf:0.1.0")`. It works on the class path and on the module path: `requires io.github.yuraburyakov.casttomarkdown;` is enough, the format modules are found as services.

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

The result looks like this (real output for a DOCX with a heading, a bullet list, a table and a link):

```markdown
# Who can apply

You can apply if you:

- served in the armed forces
- were dismissed before 2000

| Impact category | Level 1 | Level 2 |
| --- | --- | --- |
| Financial loss | Low | Moderate |

See [the full guidance](https://www.gov.uk/guidance) for details.
```

Settings: `create()` uses the defaults, `builder()` changes them. Documents larger than the limit (100 MiB by default) are rejected with `DocumentTooLargeException` before parsing; a stream is read only up to the limit. The limit is on the source size, not on memory: parsers build an object model of the document, so the heap a conversion needs depends on the format and content and may be many times the file size (a 1.7 MB DOCX with 7.6 MB of text XML needed about 100 MB). A file is read from disk as needed; a stream is read into memory in full first.

```java
CastToMarkdown converter = CastToMarkdown.builder()
        .maxDocumentSize(20 * 1024 * 1024)   // 20 MiB
        .build();
```

## What it can do

[FEATURES.md](FEATURES.md) lists every feature: what it can do, what it cannot do yet, and the tests that check it. In short:

- PDF (with a text layer): paragraphs, headings, bullet lists, web links, tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice exports); running headers, footers and page numbers are removed, words split by a hyphen at a line end are joined when the document shows how, and the text inside captioned figures is left out. Scanned PDFs are rejected: run OCR first, for example with [OCRmyPDF](https://ocrmypdf.readthedocs.io/).
- DOCX: headings from paragraph styles, nested and numbered lists, tables, footnotes, links.

Errors are unchecked: `UnsupportedFormatException` for unsupported formats and scans, `DocumentTooLargeException` above the size limit, `DocumentConversionException` for unreadable, damaged or password-protected files (the original exception is the cause).

A conversion has no time limit. For untrusted uploads run it in your own executor with a timeout, as with any parser.

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
