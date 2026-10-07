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
- A new paragraph starts after a gap larger than the usual line pitch for that font size, at a first-line indent after a line that ends a sentence, when the font size changes, and when the text moves up (next column).
- A sentence cut by the end of a page stays one paragraph; a finished sentence or list item on the next page starts a new one.
- A sentence cut by the end of a column goes on at the top of the next column when it continues in lower case or after a hyphen (arXiv 1512.00567, 1608.06993, 1810.04805). Tested by `PdfConverterParagraphsTest`.
- A hanging indent of a list item stays in one paragraph, also for items numbered `I`, `II` or `[1]`: an indented line after a line that does not end a sentence is no first-line indent (arXiv 1404.7828). Tested by `PdfConverterParagraphsTest`.
- A sentence at the bottom of a page goes on past the footnotes of that page when the next page carries it on in lower case (or the line ends with a hyphen); the footnotes follow the paragraph (arXiv 1706.03762, 1712.01208, 1404.7828). Tested by `PdfConverterParagraphsTest`.
- The second line of a centred title is not taken for a first-line indent when the next line is in another font (arXiv 1810.04805). Tested by `PdfConverterHeadingsTest`.
- Lines that pdfTeX (microtype) stretches a little keep the font size of the paragraph, so they do not start a new paragraph (arXiv 1706.03762). A font scaled wider on purpose keeps the size PDFBox gives it. Tested by `LineCollectorTest`.

- Text outside the page or outside the box of an embedded figure (a form XObject) is cut away and left out (the title of a cropped figure in arXiv 1706.03762 under a section heading; a full stop past the page edge). Tested by `LineCollectorTest`.
- Invisible text (rendering mode 3) is left out on a page that has visible text, so it neither shows up nor removes visible letters it overlaps (Word 365 put an older wording of a note invisibly under the visible one). On a page with only invisible text, such as the OCR layer of a scan, it is the text. Visible text drawn twice for boldness comes once. Tested by `PdfInvisibleTextTest`.
- A justified line where the space character is narrower than the stretched gap gives one space, not two (LibreOffice 7.3, `The  foundation  promotes`). Tested by `LineCollectorTest`.
- A space drawn over a letter or digit gives no space (LibreOffice 7.3 draws spaces over the letters of a link: `http  s  ://` becomes `https://`). Tested by `LineCollectorTest`.

**Cannot**
- Text hidden by other means (white on white, covered by a box drawn later, cut by a clipping path drawn with `W`) is still output. Not covered.
- An OCR layer on a page that also has some visible text (a stamp, a printed page number) is left out with the other invisible text. Not covered.
- A sentence cut by a footnote at the bottom of a page stays split when the next page starts with a capital letter, or when the two parts are in different columns. Not covered.
- Footnotes and floating blocks (figures, tables from another column) can sit between the parts of a sentence.

**Tests:** `PdfConverterParagraphsTest`, `CastToMarkdownTest`

### Text drawn after its line

**Can**
- Text a generator draws after the rest of its line (WeasyPrint: link text such as `[RFC4122]`, numbers of list items) goes back into the line when it sits exactly on the same baseline, in the same font size, and fits a gap between the words, touches the first word, or follows the last word closely. A list number or bullet may stand up to half the font size before the first word. Tested by `PdfLateTextTest`.
- Text in a font up to 1 pt smaller or larger fills only a gap between two words, with a list number before them (RFC 9562 draws `MUST` and `MAY` 1 pt smaller); after the last word it stays apart, as pieces of formulas would join there. Tested by `LateTextTest`.
- Text ending with a word split by a hyphen that the next line carries on does not go after the end of a line a few lines back (arXiv 1512.00567: `abil-` / `ity` across the denominator of a fraction stays one word). Tested by `LateTextTest`.
- Text far from the line (the value column of a title block, a page number in a table of contents, the other column) and a row label a few points left of a table row stay apart. Tested by `PdfLateTextTest`.

**Cannot**
- Labels of a reference list (`[C309]`) stay on their own line before the entry; so do the quoted keywords of the BCP 14 paragraph and a citation 2.6 pt before the first word of its line. Not covered.
- In formulas of pdfTeX papers a piece on the same baseline joins its line too (arXiv 1404.7828: `for all k ∈ int add to4wv(k,t) the value xkδt` becomes one line). Not covered.

### Headings

**Can**
- Text larger than the body font is a heading; levels follow the font sizes, largest first.
- A second plain body size up to 1 pt larger is not treated as an unnumbered heading when it has at least ten long lines, at least half the primary size's long-text weight, and at least ten close, aligned alternations with the primary size. Repeated bold headings and fonts used in separate sections do not establish this second size in the tested cases.
- Bold body-size text that starts with a section number (`2.1 Scope`) is a heading with the level from the number; `§` headings are split from the text that follows.
- A heading is at most one level deeper than the one before; a two-line heading is joined; a title in a much larger font may take up to four lines.
- A heading of up to four short centred lines is one heading (`Appendix for “BERT: ...”`); a centred notice of long lines is not a heading. Tested by `PdfConverterHeadingsTest`.
- `Abstract` alone on a line is a heading on the first two pages in any font (arXiv sets it in the small font of the abstract), and bold on any page (NIST). Tested by `PdfConverterHeadingsTest`.
- An unnumbered first heading on the first page is level 1 when no heading has that level, also in the font of the numbered sections (which are level 2). Tested by `PdfConverterHeadingsTest`.
- A title on the first page in a font at least 1.5 times the body size, or the first paragraph of the document in a font at least 1.25 times the body size, is a heading also when the authors follow in a font a little larger than the body. Tested by `PdfConverterHeadingsTest`.
- Not taken for headings: a bold numbered list item out of sequence, a table of contents entry, a long paragraph in a large font, text fragments without words, large text followed by small figure text, a mostly bold first line of a definition, a date alone (`1 October 2026`), and the authors right after a first-page title when e-mail addresses follow them. Tested by `PdfConverterHeadingsTest`.

**Cannot**
- Bold headings without a number in body size are not found, except `Abstract`; a title on a cover page may not be found.
- The mixed-body thresholds are conservative heuristics, not a general font classifier. This change does not repair arbitrary paragraph splitting or every missing heading.

**Tests:** `PdfConverterHeadingsTest`, `HeadingsBodyFontsTest`

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
- Running text with words found in one place on many pages also goes from the edge of the other pages, wherever it is there (`Return to Contents` placed elsewhere on the covers). Tested by `PageFurnitureTest`.

**Cannot**
- Needs at least three pages: in one- or two-page documents headers and footers stay in the text.
- Text at the page edge that only happens to equal a running header or footer is removed too. Not covered.

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
- A link broken over lines of a paragraph is one link with a line break in its text (`[Distributed Computing\nEnvironment](url)`). Tested by `PdfLinksTest`.

**Cannot**
- Links inside PDF tables, on rotated pages and on rotated text stay text.
- Two links to the same address on following lines of a paragraph are taken for one link, also when they were two (a rare list of two links to one page). Addresses with parentheses stay two links. Not covered.

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
- A title of the drawing in a much larger font just above it is removed too (`Input-Input Layer5` over the attention plots of arXiv 1706.03762); a heading of body size above a figure stays. Tested by `FiguresTest`.
- Labels beside the drawing are found within the caption's width (arXiv Figure 2: `F(x) + x` left of the blocks).
- A figure drawn in several pieces (columns of a diagram, a column cut by text) is one figure, within the caption's column; a frame touching the caption still belongs to it.
- Only lines of at most 8 words are labels: longer lines in a figure (rows of a table drawn as a figure, sentences) always stay.
- In a table drawn as a figure, a short line left of a longer row on the same baseline is the row's label and stays (`page size: 64` in arXiv 1712.01208 Figure 4). Tested by `FiguresTest`.
- Only what is visible counts as drawn: a placed picture's background clipped away under the next column does not join the columns.
- Never taken for a figure: a table of thin rules, a page background, a figure in the other column, `Fig. 4.` in the middle of a paragraph, a caption without graphics next to it.
- Left as they are (untrusted input): pages painting more than 10,000 boxes, or with more than 200 drawings or 50 captions.

**Cannot**
- A figure without such a caption keeps its text; rotated pages are skipped.
- Labels more than one caption font size above or below the drawing stay (arXiv Figure 3: the network names over its columns), unless a line is in a font at least 1.5 times the caption's and at most two of its own font sizes above the drawing. Tested by `FiguresTest`.
- A short label line inside a table drawn as a figure is removed with the other labels unless it is a row label on the baseline of a longer row (column headers such as `Lookup (ns)`, labels spanning rows).
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
- A standalone `×10^E` is emitted as Unicode only at the end of the first row's cell in a recognised ruled table when that same cell contains the label `params` (case-insensitive). The existing glyph-size/baseline/gap checks still apply. Other cells, data rows and ordinary prose keep it unchanged.
- In recognised ruled tables, convert a scientific expression inside one word in its existing cell; preserve columns and empty cells. Never use another word as context in those table cells.
- Leave footnote-like word endings, ordinary baseline numbers, standalone `10` powers without a numeric multiplier, dollar signs and existing Unicode unchanged in the tested cases.
- Disable detection in tagged documents (tested). Geometry and surrounding text are preserved by the transformation.

**Cannot**
- This first version only handles `M×10^E`: M uses ASCII digits and an optional decimal point; E is 1–3 positive ASCII digits. No negative signs, general formulas, lower indices or LaTeX output. Standalone scales have only the narrow `params` header exception above.
- A raised footnote immediately following `×10` in a `params` header is geometrically ambiguous with a power; the header rule does not prove the absence of a footnote. Other header labels and punctuation around the scale remain outside this step.
- Cross-word expressions in recognised tables stay text. Tables the existing table detector does not recognise receive the ordinary-text rules; those are not a guarantee of cell boundaries.
- Detection is also disabled on rotated pages and lines containing URL links (code guards; no end-to-end regression fixture yet).
- Thresholds are based on ResNet measurements, not calibrated across PDF generators. A raised digit directly after scientific notation can still be ambiguous with a footnote.

**Tests:** `ScientificPowersTest` (synthetic geometry, a generated PDF, ruled-table fixture); the local sample comparison is recorded separately in the AI work log.

### arXiv margin stamp

**Can**
- Reconstruct a complete, fragmented modern arXiv stamp on page 1 as one plain Markdown block, retaining the identifier, version, category and date at the original output position.
- Require adjacent single-line rotated paragraphs, a consistent left-margin band, contiguous increasing text coordinates, and at least three substantial horizontal lines of the dominant text size to establish a margin.
- Reject missing or corrupted stamp parts, interrupted chains, text inside the body, page 2 and ordinary short text. Keep rotated axis labels and existing heading output unchanged in the tested cases.
- Serialize after paragraph and heading analysis, without changing the original Line geometry or those analyses.

**Cannot**
- No general reconstruction of rotated text, older arXiv identifiers, right-margin stamps or sparse pages without a confident text margin.
- Production conversion disables this rule when the first page is rotated; the unit fixtures check the disabled serialization path, not a generated rotated PDF.
- Geometry tolerances are conservative hypotheses based on ResNet; this does not promise support for every PDF generator.
- This cosmetic change does not repair sentences interrupted by footnotes, captions or column transitions.

**Tests:** `ArxivStampTest` (seven tests with measured ResNet Line fixtures and negative cases). The separate before/after sample comparison is recorded in the AI work log.

## DOCX

Word 2007+ files, based on Apache POI.

### Headings and paragraphs

**Can**
- Headings come from the paragraph styles (`Title`, `Heading 1..6`); bold text without a heading style stays a paragraph.
- Empty paragraphs are skipped; block syntax at the start of a paragraph or list item is escaped.
- Not covered by a test: running headers and footers are left out.
- Body-level content controls (`SDT`) keep their text at the original position, with block syntax escaped on each line; empty controls add no block. Inline controls retain their existing behaviour.

**Cannot**
- Internal heading/list/table/link structure inside a body-level SDT is flattened to the text exposed by POI. Its paragraphs become lines of one Markdown text block, rather than separate Markdown paragraphs. Table-of-contents controls are retained as text; SDT is not a signal to discard content.

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
