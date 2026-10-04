# CastToMarkdown

Java-native library that converts common document formats into clean, LLM/RAG-friendly Markdown through one consistent API, using mature Java parsers under the hood.

> **Status:** early development (`0.1.0-SNAPSHOT`), preparing the first release `0.1.0`; the API may still change before `1.0`. PDF: paragraphs, headings, bullet lists, headers/footers removed, tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice exports) and captioned ruled tables of untagged ones, web links. DOCX: headings, lists, tables, footnotes, links.

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

PDF: headings are found by font size and boldness and by section numbers (`2.1 Scope`), paragraphs by line spacing; bullet lists become `-` items; running headers, footers and page numbers are removed; tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice exports) become Markdown tables, and so do tables of untagged PDFs (LaTeX, RFCs) drawn as a grid of rules with a caption such as "Table 1." next to them; links to web addresses become `[text](url)`. Password-protected PDFs are rejected with `DocumentConversionException`; PDFs that only restrict printing or copying are converted.

DOCX: headings come from the paragraph styles (`Title`, `Heading 1..6`); bold text without a heading style stays a paragraph. Lists keep their nesting and numbering, tables become Markdown tables, footnotes become Markdown footnotes, external links become `[text](url)`; running headers and footers are left out. Password-protected DOCX files and old `.doc` files renamed to `.docx` are rejected with `DocumentConversionException`. Apache POI logs through Log4j API: without a Log4j provider (or the `log4j-to-slf4j` bridge that Spring Boot includes) it prints one `Log4j API could not find a logging provider` line to stderr.

Errors are unchecked: `UnsupportedFormatException` for unsupported formats, `DocumentConversionException` for unreadable or damaged files (the original exception is the cause).

Scanned PDFs (pages are images without a text layer) are not supported: the converter throws `UnsupportedFormatException` instead of returning empty Markdown. Run OCR first, for example with [OCRmyPDF](https://ocrmypdf.readthedocs.io/), which adds a text layer to the PDF; the result can then be converted.

## Limitations

- Scanned PDFs need OCR first (see above); a PDF where only some pages are scans is not detected.
- PDF: a paragraph split by a footnote at the bottom of the page stays split in two; a word split by a hyphen at a line end is joined only when the document writes it elsewhere, with or without the hyphen; nested lists are not detected; text inside a figure (chart labels, diagram boxes) is left out only when the figure has a caption such as "Figure 1." next to it, otherwise it stays in the text; other tables of untagged PDFs (without vertical rules or without a caption, as in many web-to-PDF tools) stay plain text; in a ruled table, columns without a rule between them share one cell, and rules drawn inside cells (large brackets of a matrix) can split a row in two.
- PDF: running headers and footers are recognized when they repeat on at least three pages; in one- or two-page documents they stay in the text.
- Tables: merged cells are not spread over the columns they span; links inside PDF tables stay plain text.
- Markdown escaping covers block syntax at the start of a line (`#`, `>`, code fences, rule and heading-underline lines such as `---`, `***`, `===`, a lone `-` or `*`). List markers (`-`, `*`, `1.`) and inline syntax (`*`, `_`, `` ` ``, `[`, `<`) are kept as they are: PDFs write real lists as plain text.
- The first PDF that uses fonts it does not embed makes PDFBox scan the system fonts once and save a font cache (`.pdfbox.cache` in the user home); with hundreds of fonts, as on Windows, that takes about a minute. Later conversions and later runs reuse the cache.
- A conversion has no time limit. For untrusted uploads run it in your own executor with a timeout, as with any parser.

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
