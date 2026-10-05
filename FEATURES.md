# Features

What CastToMarkdown can and cannot do, feature by feature. Status: `0.1.0-SNAPSHOT`, nothing released yet.

How to read this file:

- **Can** lists only behaviour that a test checks; the test classes are named under **Tests**. A claim without a test says so.
- **Cannot** lists known limits. The output there is still correct text; it just keeps less structure.
- A change to a feature's behaviour updates its section in the same commit.

Contents: [API](#api) · [PDF](#pdf) · [DOCX](#docx)

## API

### Converter and settings

`CastToMarkdown.create()` or `CastToMarkdown.builder()...build()`; the result is `PreparedDocument.markdown()`.

**Can**
- One immutable instance converts from many threads at once with the same result as one by one.
- `maxDocumentSize` (default 100 MiB): a larger document is rejected with `DocumentTooLargeException` before parsing; a document of exactly the limit is converted; `Long.MAX_VALUE` means no limit; a limit of 0 or less is rejected.
- A built converter does not change when its builder changes later.

**Cannot**
- No time limit for a conversion: run untrusted uploads in your own executor with a timeout.
- The size limit is on the source, not on memory: a 1.7 MB DOCX with 7.6 MB of text XML needed about 100 MB of heap.
- No metadata, warnings or other settings yet.

**Tests:** `CastToMarkdownConcurrencyTest`, `CastToMarkdownSettingsTest`

### Input

**Can**
- `convert(Path)` and `convert(InputStream, fileName)` give the same Markdown.
- The file name's extension selects the format, ignoring case (`REPORT.PDF`).
- The caller's stream is read to the end and never closed, also when the conversion fails; a stream is read only up to the size limit.
- An unsupported extension is rejected before the stream is read.
- `null` arguments throw `NullPointerException`.

**Cannot**
- No content sniffing: a PDF named `report.docx` is read as DOCX and fails.
- A stream is read into memory in full before parsing; a file is read as needed.

**Tests:** `CastToMarkdownInputStreamTest`, `CastToMarkdownTest`

### Errors

**Can**
- All errors are unchecked: `UnsupportedFormatException` (unknown format, scanned PDF), `DocumentTooLargeException`, and `DocumentConversionException` for unreadable or damaged files, with the original exception as the cause.
- Unchecked failures inside the parsers on damaged files also become `DocumentConversionException`.

**Tests:** `CastToMarkdownTest`, `PdfRobustnessTest`, `DocxConverterTest`, `CastToMarkdownInputStreamTest`

### Format modules

Add `cast-to-markdown-pdf`, `cast-to-markdown-docx` or both; they are found with `ServiceLoader`.

**Can**
- Works on the class path and on the module path: `requires io.github.yuraburyakov.casttomarkdown;` is enough, the format modules and their parser modules come in through the service binding.
- Without any format module, the error says which dependency to add.

**Cannot**
- No public SPI for your own formats yet.

**Tests:** `ModulePathTest`, `CastToMarkdownWithoutFormatsTest`

### Markdown output

**Can**
- Line endings become `\n`, trailing spaces are removed, runs of blank lines become one, the text ends with one `\n`.
- Block syntax at the start of a line is escaped: `#`, `>`, code fences, and the rule or heading-underline lines CommonMark reads as syntax (`---`, `***`, `===`, a lone `-` or `*`).
- Links: only `http`, `https` and `mailto` become `[text](url)`; brackets in the text and parentheses and control characters in the address are escaped; a link whose text is just its address, or a piece of it, stays text.

**Cannot**
- List markers (`-`, `*`, `1.`) and inline syntax (`*`, `_`, `` ` ``, `[`, `<`) in the text are not escaped: PDFs write real lists as plain text.

**Tests:** `MarkdownTest`

## PDF

PDFs with a text layer, based on Apache PDFBox.

### Paragraphs

**Can**
- A paragraph's lines stay on separate lines; paragraphs and pages are separated by a blank line.
- A new paragraph starts after a gap larger than the usual line pitch for that font size, at a first-line indent, when the font size changes, and when the text moves up (next column).
- A sentence cut by the end of a page stays one paragraph; a finished sentence or list item on the next page starts a new one.
- A hanging indent of a list item stays in one paragraph.

**Cannot**
- A paragraph split by a footnote at the bottom of a page stays split in two.
- Footnotes and floating blocks (figures, tables from another column) can sit between the parts of a sentence.

**Tests:** `PdfConverterParagraphsTest`, `CastToMarkdownTest`

### Headings

**Can**
- Text larger than the body font is a heading; levels follow the font sizes, largest first.
- Bold body-size text that starts with a section number (`2.1 Scope`) is a heading with the level from the number; `§` headings are split from the text that follows.
- A heading is at most one level deeper than the one before; a two-line heading is joined; a title in a much larger font may take up to four lines.
- Not taken for headings: a bold numbered list item out of sequence, a table of contents entry, a long paragraph in a large font, text fragments without words, large text followed by small figure text, a mostly bold first line of a definition.

**Cannot**
- Bold headings without a number in body size are not found (`Abstract` in NIST documents); a title on a cover page may not be found.

**Tests:** `PdfConverterHeadingsTest`

### Bullet lists

**Can**
- Bullet items become `- ` items, also when the bullet is a separate piece of text next to its line.

**Cannot**
- Nested lists are not detected; numbered items stay as they are written (`1.`).

**Tests:** `ListsTest`

### Running headers, footers and page numbers

**Can**
- Text repeated at the top or bottom edge of the pages is removed, with page numbers, Roman or Arabic; so is rotated margin text repeated on every page.
- Repeated text in the middle of a page, and numbers at the page edge that change place, are kept.

**Cannot**
- Needs at least three pages: in one- or two-page documents headers and footers stay in the text.

**Tests:** `PageFurnitureTest`

### Words split by a hyphen at a line end

**Can**
- `learn-` / `ing` is joined when the document writes `learning` elsewhere; `multi-` / `layer` keeps its hyphen when the document writes `multi-layer` elsewhere.
- Nothing is guessed: when the document shows neither form, or both, the lines stay as they are.

**Cannot**
- A word the document writes only once stays split (`computa-` / `tional`).

**Tests:** `HyphensTest`

### Links

**Can**
- Link annotations to web addresses become `[text](url)`, in tagged and untagged PDFs.
- A link box that ends inside a word does not split the word; internal links, unsafe addresses and links to themselves stay text.

**Cannot**
- Links inside PDF tables, on rotated pages and on rotated text stay text; a link broken over two lines becomes two links.

**Tests:** `PdfLinksTest`, `MarkdownTest`

### Tables of tagged PDFs

Word, InDesign, Chrome and LibreOffice exports tag tables in the structure tree.

**Can**
- A tagged table becomes a Markdown table where it stands, also when it continues over several pages.
- The first row is the header; a key-value table (header cells in the first column, like a Wikipedia infobox) gets an empty header row.
- Columns empty in every row are dropped; `|` in a cell is escaped.
- Untagged text on a row's line (Chrome prints link addresses there) does not repeat the row.
- A one-row layout table stays text.
- Hostile structure trees (very deep, looping or shared elements) are walked safely.

**Cannot**
- Merged cells are not spread over the columns they span.
- Tables of untagged PDFs: see the next section.

**Tests:** `PdfTablesTest`, `TaggedTablesTest`

### Tables of untagged PDFs

LaTeX and similar tools draw tables as a grid of rules, without tags.

**Can**
- A grid of thin rules (at least two horizontal and one vertical) with a caption `Table N.` or `Table N:` right above or below it becomes a Markdown table where it stands; the caption stays.
- Columns run between the vertical rules, so empty cells are kept: arXiv's `ensemble 59.0 37.4` becomes `| ensemble |  |  | 59.0 | 37.4 |`.
- A cell spanning columns (no vertical rule there in that row) puts its text in the first of them.
- Rows between two horizontal rules are one row per text line when every line has a label in the first column, else one row whose cells join their lines.
- A caption right under the rows in their font is found, also when it ends up in their paragraph; `Table 2 shows` in a sentence is no caption.
- Left as text: rules without a vertical, a grid next to text of another column on the same lines, a cluster of more than 1,000 rules (untrusted input).
- Words keep their ligatures decomposed (`refinement`, not `re` + U+FB01 + `nement`), as in the rest of the text.

**Cannot**
- Tables without vertical rules (booktabs) or without a caption stay plain text, as do tables of many web-to-PDF tools.
- Columns without a rule between them share one cell (arXiv Table 10: 20 per-class columns are one cell, numbers in header order).
- Rules drawn inside cells (large brackets of a matrix, arXiv Table 1) can split a row in two and put bracket pieces into cells; all words are kept and in the right columns.
- RFC tables stay text: no inner vertical rules, and the page background joins the rules into one drawing.

**Tests:** `RuledTablesTest`, `PdfRuledTablesTest`, `LineCollectorTest`, `PageGraphicsTest`

### Text inside figures

**Can**
- In untagged PDFs (LaTeX), the labels of a vector figure (axis labels, diagram boxes, vertical axis titles) are left out when the figure has a caption `Figure N.`, `Figure N:` or `Fig. N.` right above or below it; the caption stays, once.
- Labels beside the drawing are found within the caption's width (arXiv Figure 2: `F(x) + x` left of the blocks).
- A figure drawn in several pieces (columns of a diagram, a column cut by text) is one figure, within the caption's column; a frame touching the caption still belongs to it.
- Only lines of at most 8 words are labels: longer lines in a figure (rows of a table drawn as a figure, sentences) always stay.
- Only what is visible counts as drawn: a placed picture's background clipped away under the next column does not join the columns.
- Never taken for a figure: a table of thin rules, a page background, a figure in the other column, `Fig. 4.` in the middle of a paragraph, a caption without graphics next to it.
- Left as they are (untrusted input): pages painting more than 10,000 boxes, or with more than 200 drawings or 50 captions.

**Cannot**
- A figure without such a caption keeps its text; rotated pages are skipped.
- Labels more than one caption font size above or below the drawing stay (arXiv Figure 3: the network names over its columns).
- A short label line inside a table drawn as a figure (a header such as `page size`) is removed with the other labels.
- Figures are not kept as images.

**Tests:** `FiguresTest`, `PdfFiguresTest`, `PageGraphicsTest`

### Scanned, encrypted and damaged PDFs

**Can**
- A PDF whose pages are only images (a scan) is rejected with `UnsupportedFormatException` instead of empty Markdown; run OCR first, for example with [OCRmyPDF](https://ocrmypdf.readthedocs.io/).
- A PDF with text and images is converted; a PDF without text or images gives empty Markdown.
- Damaged files fail with `DocumentConversionException`.
- Not covered by a test: password-protected PDFs are rejected with `DocumentConversionException`; PDFs that only restrict printing or copying are converted.

**Cannot**
- A PDF where only some pages are scans is not detected; no OCR.
- The first PDF that uses fonts it does not embed makes PDFBox scan the system fonts once and save a font cache (`.pdfbox.cache` in the user home); with hundreds of fonts, as on Windows, that takes about a minute.

**Tests:** `CastToMarkdownTest`, `PdfRobustnessTest`

### Scientific powers in untagged PDFs

**Can**
- Write a raised positive integer exponent as Unicode in scientific notation: `60× 104` becomes `60× 10⁴`, and `1.8×109` becomes `1.8×10⁹`, when glyph sizes, baselines and spacing identify the exponent.
- Use a nearby numeric multiplier ending in `×` in ordinary text, rejecting a different baseline, a large gap, intervening text or a vertical rule.
- In recognised ruled tables, convert a scientific expression inside one word in its existing cell; preserve columns and empty cells. Never use another word as context in those table cells.
- Leave footnote-like word endings, ordinary baseline numbers, standalone `10` powers without a numeric multiplier, dollar signs and existing Unicode unchanged in the tested cases.
- Disable detection in tagged documents (tested). Geometry and surrounding text are preserved by the transformation.

**Cannot**
- This first version only handles `M×10^E`: M uses ASCII digits and an optional decimal point; E is 1–3 positive ASCII digits. No negative signs, general formulas, lower indices or LaTeX output.
- Cross-word expressions in recognised tables stay text. Tables the existing table detector does not recognise receive the ordinary-text rules; those are not a guarantee of cell boundaries.
- Detection is also disabled on rotated pages and lines containing URL links (code guards; no end-to-end regression fixture yet).
- Thresholds are based on ResNet measurements, not calibrated across PDF generators. A raised digit directly after scientific notation can still be ambiguous with a footnote.

**Tests:** `ScientificPowersTest` (synthetic geometry, a generated PDF, ruled-table fixture); the local sample comparison is recorded separately in the AI work log.

## DOCX

Word 2007+ files, based on Apache POI.

### Headings and paragraphs

**Can**
- Headings come from the paragraph styles (`Title`, `Heading 1..6`); bold text without a heading style stays a paragraph.
- Empty paragraphs are skipped; block syntax at the start of a paragraph or list item is escaped.
- Not covered by a test: running headers and footers are left out.

**Cannot**
- Content controls (`SDT`) at body level are skipped.

**Tests:** `DocxConverterTest`

### Lists

**Can**
- Bullet lists keep their nesting.
- Numbered lists keep the document's numbering: start values, start overrides (Word's Restart Numbering), and lists that share one definition continue its count.

**Cannot**
- Letter and Roman numbering become numbers (CommonMark knows only numbers).
- The shared-definition case is confirmed by external sources, not yet by opening the file in Word.

**Tests:** `DocxConverterTest`

### Tables

**Can**
- Tables become Markdown tables; the paragraphs of a cell are separated, links in cells are kept.

**Cannot**
- Merged cells are not spread over the columns they span.

**Tests:** `DocxConverterTest`

### Footnotes

**Can**
- Footnotes become Markdown footnotes (`[^1]`).

**Tests:** `DocxConverterTest`

### Links

**Can**
- External links become `[text](url)`, also when Word splits one link over several runs.
- Internal and unsafe links stay text; an address shown as its own link text stays plain text; brackets and parentheses are escaped.

**Cannot**
- Links made with `HYPERLINK` fields stay plain text.

**Tests:** `DocxConverterTest`, `MarkdownTest`

### Protected, old and damaged files

**Can**
- A password-protected DOCX, or an old `.doc` file renamed to `.docx`, fails with a `DocumentConversionException` that says so.
- A damaged DOCX fails with `DocumentConversionException`; the caller's stream is not closed.
- A DOCX is also read from a `Path`.

**Cannot**
- Old `.doc` files are not supported.
- Apache POI logs through Log4j API: without a Log4j provider (or the `log4j-to-slf4j` bridge that Spring Boot includes) it prints one `Log4j API could not find a logging provider` line to stderr.

**Tests:** `DocxConverterTest`
