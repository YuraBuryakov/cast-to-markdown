# CastToMarkdown

Java-native library that converts common document formats into clean, LLM/RAG-friendly Markdown through one consistent API, using mature Java parsers under the hood.

> **Status:** release `0.2.0`, early development; the API may still change before `1.0`. PDF: paragraphs, headings, bullet lists, headers/footers removed, tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice exports) and captioned ruled tables of untagged ones, web links. DOCX: headings, lists, tables, footnotes, links. HTML: the main content of web pages without navigation and page furniture. TXT, CSV and TSV. XLSX (in development for 0.3.0): each sheet as a Markdown table.

## Goals

- One small Java API for several formats.
- Runs embedded in the JVM: no Python, Docker or external service for the basic formats.
- Markdown that keeps useful structure (headings, lists, links, simple tables) and drops obvious extraction noise.
- Built on mature libraries (Apache PDFBox, Apache POI, jsoup) instead of custom parsers.

## Installation

Add the format modules you need (Java 17+); each brings `cast-to-markdown-core` with it:

```xml
<dependency>
    <groupId>io.github.yuraburyakov</groupId>
    <artifactId>cast-to-markdown-pdf</artifactId>
    <version>0.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.yuraburyakov</groupId>
    <artifactId>cast-to-markdown-docx</artifactId>
    <version>0.2.0</version>
</dependency>
```

The other formats come the same way: `cast-to-markdown-html` (web pages, jsoup), `cast-to-markdown-txt`, `cast-to-markdown-csv` (CSV and TSV), all `0.2.0`; `cast-to-markdown-xlsx` (Excel) comes with `0.3.0`.

Gradle: `implementation("io.github.yuraburyakov:cast-to-markdown-pdf:0.2.0")`. It works on the class path and on the module path: `requires io.github.yuraburyakov.casttomarkdown;` is enough, the format modules are found as services.

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

The document's own title, author and language come with the Markdown, when it states them (PDF document information, DOCX properties, HTML `<title>`, `<meta name="author">` and `<html lang>`):

```java
document.title().ifPresent(title -> index.put("title", title));   // Optional<String>
document.language();                                             // Optional<String>, such as "en-US"
```

A web page fetched by your code: pass its address, and relative links (`/docs/intro`) become absolute links.

```java
PreparedDocument page = converter.convert(in, "page.html", response.uri());
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

- PDF (with a text layer): paragraphs, headings (also from the structure tree of a tagged PDF), bullet lists, web links, tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice exports) and captioned ruled tables of untagged ones (LaTeX), empty cells included; running headers, footers and page numbers are removed, a word split by a hyphen at a line end is joined, and the hyphen of a compound stays, and the text inside captioned figures is left out. Scanned PDFs are rejected: run OCR first, for example with [OCRmyPDF](https://ocrmypdf.readthedocs.io/).
- DOCX: headings from paragraph styles, nested and numbered lists, tables, footnotes, links.
- HTML web pages (from 0.2.0): the main content without navigation, page header and footer, sidebars and hidden elements; headings, lists, tables, code blocks, quotes, links resolved against the page's own address.
- TXT and CSV/TSV (from 0.2.0): plain text with block syntax escaped; CSV and TSV as a Markdown table, RFC 4180 quoting, comma, semicolon or tab found by itself.
- XLSX (from 0.3.0): each visible sheet as a Markdown table with the values Excel shows (number and date formats, saved formula results), a heading per sheet when there are several.

Errors are unchecked: `UnsupportedFormatException` for unsupported formats and scans, `DocumentTooLargeException` above the size limit, `DocumentConversionException` for unreadable, damaged or password-protected files (the original exception is the cause).

A conversion has no time limit. For untrusted uploads run it in your own executor with a timeout, as with any parser.

## How it compares

Measured against [OpenDataLoader PDF](https://github.com/opendataloader-project/opendataloader-pdf) 2.5.12, the other pure-Java PDF-to-Markdown library, on 12 public PDFs that were not used to tune this library. They come from different generators: pdfTeX (one- and two-column arXiv papers, revtex, ACM), Ghostscript, Acrobat Distiller, InDesign, Word, Aspose.Words, LibreOffice, Google Docs and WeasyPrint (an RFC). OpenDataLoader ran with its defaults (`hybrid=off`, `readingOrder=xycut`) and again with `useStructTree=true`. Both ran on OpenJDK 21 in separate JVMs, and the time is the second (warm) pass.

| | CastToMarkdown 0.2.0 | OpenDataLoader 2.5.12 (default / `useStructTree`) |
|---|---|---|
| Words of the PDF text kept (reference: `pdftotext -raw`, 143,012 words) | 97.5 % | 96.6 % / 96.5 % |
| Headings of the PDF outline found (312 entries in 7 files) / headings that are not in the outline | 269 / 25 | 237 / 89; 255 / 84 |
| Web links written as Markdown links | 362 | 0 |
| Compounds that lost their hyphen at a line break | 0 | 21 |
| Statistical tables (BLS Employment Situation, Fed H.4.1) as Markdown tables | 0 of 2 files | 43 tables in 2 files (11 with `useStructTree`) |
| Time for the 12 files | 1.9 s | 3.4 s |
| Runtime jars | 6, 4.0 MB | 33, 25 MB |

Where OpenDataLoader does better:
- Tables of statistical reports drawn without row rules become Markdown tables there and plain text here.
- Text inside figures and formulas is broken into many short paragraphs here.
- Some words split at a line end keep their hyphen here when the rest is itself a word or the word is not in the 40,000-word list (`com-pact`, `be-fore`, `Gaus-sian`: 25 cases); OpenDataLoader joins them.

Where this library does better:
- It writes links, and it writes no `<br>`, `&lt;` or `&gt;` into the text.
- It finds fewer false headings.
- It keeps the hyphen of compounds (`anti-dumping`, `single-shot`).

The fix of running headers in 0.2.0 was prompted by two of the files (Fed H.4.1, BLS) and tuned on two other Fed releases (Z.1, H.8); the compared files were run once after it. Two more files were left out of these numbers because they were used to tune this library after the first comparison: a tagged Typst PDF (headings of tagged PDFs) and the Python documentation printed by Chrome (lines drawn out of order). How both libraries do on them is in the benchmark notes. The files with their download links, how each number was taken and the numbers per file are in [docs/benchmark.md](docs/benchmark.md).

### HTML

Measured on 10 public pages not used to develop the module, one per site type (Sphinx, MkDocs, Docusaurus, Javadoc, a blog, MediaWiki, usa.gov, GitHub, a Fed data table, a news article), against copy-down 1.1, the Java port of Turndown, which converts the whole page:

| | CastToMarkdown | copy-down |
|---|---|---|
| Words of the page's main text kept (mean per page) | 95.0 % | 99.6 % |
| Words of the output from outside the main text (mean per page) | 1.5 % | 32.8 % |
| Raw HTML a renderer would hide | 0 | 8 |

CastToMarkdown leaves out navigation, page header and footer and hidden elements, also inside `<main>`, which costs words where those are part of the main element (a GitHub pull request, usa.gov). copy-down keeps the whole page. Pages saved without their address lose relative links here; copy-down keeps them as relative paths (the commons-lang Javadoc: 759 links against 1,986). How the numbers were taken and the numbers per page are in [docs/benchmark-html.md](docs/benchmark-html.md).

## Formats

| Format | Version | Module |
|---|---|---|
| PDF (with a text layer) | 0.1 | `cast-to-markdown-pdf` |
| DOCX (Word 2007+) | 0.1 | `cast-to-markdown-docx` |
| HTML (web pages) | 0.2 | `cast-to-markdown-html` |
| TXT, CSV, TSV | 0.2 | `cast-to-markdown-txt`, `cast-to-markdown-csv` |
| XLSX (Excel 2007+) | 0.3 | `cast-to-markdown-xlsx` |

## Project structure

| Module | Artifact | Content |
|---|---|---|
| `core` | `cast-to-markdown-core` | the API (`io.github.yuraburyakov.casttomarkdown`); no parser dependencies |
| `pdf` | `cast-to-markdown-pdf` | PDF, based on Apache PDFBox |
| `docx` | `cast-to-markdown-docx` | DOCX (Word 2007+), based on Apache POI |
| `html` | `cast-to-markdown-html` | HTML web pages, based on jsoup (from 0.2.0) |
| `txt` | `cast-to-markdown-txt` | plain text, no dependencies (from 0.2.0) |
| `csv` | `cast-to-markdown-csv` | CSV and TSV as a Markdown table, no dependencies (from 0.2.0) |
| `xlsx` | `cast-to-markdown-xlsx` | XLSX (Excel 2007+) sheets as Markdown tables, based on Apache POI (from 0.3.0) |

Add the format modules you need; they depend on `core` and are found automatically (`ServiceLoader`), so users who need only PDF do not pull other parsers. `io.github.yuraburyakov.casttomarkdown` is the only API package; the converter contract in `internal` is exported only to the format modules and may change in any version.

## Build

Requires JDK 17+ and Maven 3.6.3+.

```bash
mvn verify
```

## License

[Apache License 2.0](LICENSE)
