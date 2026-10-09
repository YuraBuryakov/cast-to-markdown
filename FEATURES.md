# Features

What CastToMarkdown can and cannot do, feature by feature. Status: `0.2.0` released; `main` is `0.3.0-SNAPSHOT`.

How to read this file:

- **Can** lists only behaviour that a test checks; the test classes are named under **Tests**. A claim without a test says so.
- **Cannot** lists known limits. The output there is still correct text; it just keeps less structure.
- A change to a feature's behaviour updates its section in the same commit.

Contents: [API](#api) · [PDF](#pdf) · [DOCX](#docx) · [HTML](#html) · [TXT](#txt) · [CSV](#csv) · [XLSX](#xlsx)

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
- No warnings or other settings yet.

**Tests:** `CastToMarkdownConcurrencyTest`, `CastToMarkdownSettingsTest`

### Metadata

`PreparedDocument.title()`, `author()` and `language()`, each an `Optional<String>`.

**Can**
- What the document states about itself, on one line without spaces around it; empty when it states nothing. PDF: title and author of the document information, `/Lang` of the catalog. Tested by `CastToMarkdownTest.metadataComesFromTheDocumentInformation`. DOCX: title, creator and language of the core properties, else the language of the default text style, where Word writes it. Tested by `DocxConverterTest.metadataComesFromTheDocumentProperties`. HTML: `<title>`, `<meta name="author">`, `<html lang>`. Tested by `HtmlConverterTest.metadataOfThePage`.
- TXT and CSV have no metadata: all three are empty. Tested by `TxtConverterTest.charsetsAndExtension`.

**Cannot**
- Values are not checked or cleaned up: a PDF made by Word may give "Microsoft Word - report.docx" as its title, and the language is not checked to be a BCP 47 tag.
- The language is not guessed from the text, and the PDF title is not read from XMP metadata when the document information has none.

### Input

**Can**
- `convert(Path)` and `convert(InputStream, fileName)` give the same Markdown.
- The file name's extension selects the format, ignoring case (`REPORT.PDF`).
- The caller's stream is read to the end and never closed, also when the conversion fails; a stream is read only up to the size limit.
- An unsupported extension is rejected before the stream is read.
- `convert(InputStream, fileName, URI source)` gives the address the document was read from, for the relative links of an HTML page (see HTML, Links); other formats ignore it.
- `null` arguments throw `NullPointerException`. Tested by `HtmlConverterTest.metadataOfThePage` for `source`.

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
- Links: only `http`, `https` and `mailto` become `[text](url)`; brackets in the text and parentheses and control characters in the address are escaped. A link whose text is just its address becomes an autolink `<https://...>` or `<team@example.org>`, which works anywhere in a line (a bare address right after a footnote number, `1http://...`, is no link to GFM); it stays text when the address has a space, `<` or `>`. A link whose text is a piece of its address stays text. Tested by `MarkdownTest`.
- Text a CommonMark renderer would take for HTML and hide is escaped in every format: `List\<E>`, `\<b>`, `\<!--`; `a < b` and `x<5` stay. In PDF and DOCX the links written by this library and an autolink the document wrote out itself (`<https://...>`) stay links; TXT and CSV escape that autolink too (see their sections). Tested by `MarkdownTest.tagsOutsideLinksAreEscapedAndLinksStay`, `PdfConverterEscapeTest.tagsInTextAreEscaped`, `DocxConverterTest.tagsInTextAreEscapedAndLinksStay`.

**Cannot**
- List markers (`-`, `*`, `1.`) and inline syntax (`*`, `_`, `` ` ``, `[`) in the text are not escaped: PDFs write real lists as plain text.

**Tests:** `MarkdownTest`

## PDF

PDFs with a text layer, based on Apache PDFBox.

### Paragraphs

**Can**
- A paragraph's lines stay on separate lines; paragraphs and pages are separated by a blank line.
- A new paragraph starts after a gap larger than the usual line pitch for that font size, at a first-line indent after a line that ends a sentence, when the font size changes, and when the text moves up (next column).
- A sentence cut by the end of a page stays one paragraph; a finished sentence or list item on the next page starts a new one.
- A sentence cut by the end of a page goes on from any column of the next page when it continues in lower case or after a hyphen (right column to the left one in two-column papers: arXiv 1608.06993, 1512.03385). Tested by `PdfConverterParagraphsTest`.
- A list item cut by the end of a page goes on at its hanging indent on the next page, in lower case (tdf-statutes.pdf). Tested by `PdfConverterParagraphsTest`.
- A sentence cut by the end of a column goes on at the top of the next column when it continues in lower case or after a hyphen (arXiv 1512.00567, 1608.06993, 1810.04805). Tested by `PdfConverterParagraphsTest`.
- A line that starts with a raised footnote number keeps the baseline of its text, so a two-line footnote stays one paragraph (arXiv 1712.01208, 1810.04805). Tested by `LineCollectorTest`.
- A raised footnote number becomes superscript digits, in the text and before the footnote: `competitions¹, where`, `¹Note, that`, `¹<http://image-net.org/...>`. One to three digits, touching a character of the text and smaller and raised as a power is (the thresholds of the scientific powers below), with a space or text on the baseline on their other side. The same rule writes a raised digit of a formula as a superscript too (`O(n² · d)`, `key²`). Tested by `LineCollectorTest.raisedFootnoteNumberIsASuperscript`, `PdfLinksTest.linkAfterARaisedFootnoteNumberIsAnAutolink`.
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
- A raised footnote number is no Markdown footnote (`[^1]`): the number in the text is not tied to the text of the footnote. Digits next to the number (`10` of `10⁶`), subscripts, more than three digits and digits inside a longer raised expression (`a^{10,000,000}`) stay as they are (`LineCollectorTest.otherSmallDigitsStayDigits`). A footnote address without a link annotation after its number (`²http://...`) is no link to GFM.
- A sentence cut by a footnote at the bottom of a page stays split when the next page starts with a capital letter, or when the two parts are in different columns. Not covered.
- Footnotes and floating blocks (figures, tables from another column) can sit between the parts of a sentence.

**Tests:** `PdfConverterParagraphsTest`, `CastToMarkdownTest`

### Text drawn after its line

**Can**
- Text a generator draws after the rest of its line (WeasyPrint: link text such as `[RFC4122]`, numbers of list items) goes back into the line when it sits exactly on the same baseline, in the same font size, and fits a gap between the words, touches the first word, or follows the last word closely. A list number or bullet may stand up to half the font size before the first word. Tested by `PdfLateTextTest`.
- Text in a font up to 1 pt smaller or larger fills only a gap between two words, with a list number before them (RFC 9562 draws `MUST` and `MAY` 1 pt smaller); after the last word it stays apart, as pieces of formulas would join there. Tested by `LateTextTest`.
- Text ending with a word split by a hyphen that the next line carries on does not go after the end of a line a few lines back (arXiv 1512.00567: `abil-` / `ity` across the denominator of a fraction stays one word). Tested by `LateTextTest`.
- A whole line drawn after the lines below it goes back between them when it lies between two following lines, starts at the lower one's left edge and is in its font size or a larger one (WeasyPrint: the linked title of an RFC 9562 reference, a late heading above its text). Tested by `LateTextTest`.
- Lines drawn after the text of their page but above all of it go to the top of the page when they start at the left edge of its first line or up to two font sizes right of it, and the late lines right below them follow (RFC 9562 list items 9 to 16 at the top of page 6; Chrome section headings of Wikipedia and Python docs, the title of the other-c7 cover before its contacts). The right column of a two-column page stays after the left one. Only on pages with at most 300 lines before the late one, so that a hostile page drawn bottom up takes linear time. Tested by `LateTextTest`.
- A reference label (`[C309]`) one font size left of its entry, on the same baseline, starts the entry, also when the generator draws all labels of the page before its heading (RFC 9562 and RFC 9457, xml2rfc with WeasyPrint). Tested by `LateTextTest.referenceLabelsDrawnBeforeTheHeadingGoToTheStartOfTheirEntries`.
- Words drawn after the punctuation between them on the same baseline, in the same font size, join it in the order of their positions: `Saint-Andre, P. and J. Klensin, "Uniform Resource Names (URNs)", RFC 8141` instead of ` and , , ,` with the names below the next entry, and the citations `[X500]` and `[RFC4122]` of RFC 9562 section 6.5 in their sentence. Two columns on one baseline do not touch each other. Tested by `LateTextTest.wordsDrawnAfterThePunctuationBetweenThemJoinIt`, `LateTextTest.linesOfTwoColumnsOnOneBaselineStayApart`.
- Text far from the line (the value column of a title block, a page number in a table of contents, the other column) and a row label a few points left of a table row stay apart. Tested by `PdfLateTextTest`.

**Cannot**
- The quoted keywords of the BCP 14 paragraph and a citation 2.6 pt before the first word of its line stay on their own line before it. Not covered.
- The first line of the first reference entry under a heading can stay before the heading when it is drawn after the page (RFC 9457 `[ABNF] Crocker, D., ...` above `7.1. Normative References`; seen once, on a file not used for tuning). Not covered.
- In formulas of pdfTeX papers a piece on the same baseline joins its line too (arXiv 1404.7828: `for all k ∈ int add to4wv(k,t) the value xkδt` becomes one line). Not covered.

### Headings

**Can**
- Text larger than the body font is a heading; levels follow the font sizes, largest first.
- A second plain body size up to 1 pt larger is not treated as an unnumbered heading when it has at least ten long lines, at least half the primary size's long-text weight, and at least ten close, aligned alternations with the primary size. Repeated bold headings and fonts used in separate sections do not establish this second size in the tested cases.
- Bold body-size text that starts with a section number (`2.1 Scope`) is a heading with the level from the number; `§` headings are split from the text that follows. Bold also counts for TeX fonts without a weight in the PDF, `NimbusRomNo9L-Medi` and `CMBX` (arXiv 1706.03762: 22 of 22 outline entries, 7 before). Tested by `LineCollectorTest`.
- A heading is at most one level deeper than the one before, and a heading of the level of the one before gets its level (`Updating your online application` after `Online applications` in an InDesign PDF, both level 2 in its outline); a two-line heading is joined; a title in a much larger font may take up to four lines. Tested by `PdfTaggedHeadingsTest`.
- A heading of up to four short centred lines is one heading (`Appendix for “BERT: ...”`); a centred notice of long lines is not a heading. Tested by `PdfConverterHeadingsTest`.
- `Abstract` alone on a line is a heading on the first two pages in any font (arXiv sets it in the small font of the abstract), and bold on any page (NIST). Tested by `PdfConverterHeadingsTest`.
- An unnumbered first heading on the first page is level 1 when no heading has that level, also in the font of the numbered sections (which are level 2). Tested by `PdfConverterHeadingsTest`.
- A title on the first page in a font at least 1.5 times the body size, or the first paragraph of the document in a font at least 1.25 times the body size, is a heading also when the authors follow in a font a little larger than the body. Tested by `PdfConverterHeadingsTest`.
- In a tagged PDF a paragraph whose lines are all in a heading element (`H1` to `H6`) is a heading of that level, also in the font of the text (Typst API documentation: 81 of its 98 outline entries, 10 before; the Wikipedia subheadings printed by Chrome). Only when it reads like a heading, as generators tag other text as headings too: at most 12 words, no full stop at the end, no `Figure`/`Table N` caption (InDesign tags unstyled body text as `H2`, NIST whole sections as `H1`). A short heading line ends its paragraph where the element ends. Tested by `PdfTaggedHeadingsTest`.
- Not taken for headings: a bold numbered list item out of sequence, a table of contents entry (with dot leaders, or the text of a later paragraph and a page number: arXiv 1404.7828), a long paragraph in a large font, text fragments without words, large text followed by small figure text, a mostly bold first line of a definition, a date alone (`1 October 2026`), and the authors right after a first-page title when e-mail addresses follow them. Tested by `PdfConverterHeadingsTest`.

**Cannot**
- Bold headings without a number in body size are not found, except `Abstract`; a title on a cover page may not be found.
- A short text that a generator tags as a heading although it is none becomes a heading (the header row of a table in other-c6). Not covered.
- The mixed-body thresholds are conservative heuristics, not a general font classifier. This change does not repair arbitrary paragraph splitting or every missing heading.

**Tests:** `PdfConverterHeadingsTest`, `HeadingsBodyFontsTest`, `PdfTaggedHeadingsTest`

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
- Repeated text at the edge stays when text of its own page is between it and the edge: the row of years under the title of each table of Fed Z.1 (`2023 2024 2025 2025:Q2 ...`), the column headings and units of statistical tables. Only the lines outside the page's own text go, as running headers and footers are. Tested by `PageFurnitureTest.repeatedTextBelowTextOfItsOwnPageStays`.

**Cannot**
- Needs at least three pages: in one- or two-page documents headers and footers stay in the text.
- Text at the page edge that only happens to equal a running header or footer is removed too. Not covered.
- Repeated text with nothing of its page between it and the edge goes even when it belongs to the content: the same source line under charts near the bottom of many pages (`Source: European Commission.` in an InDesign factsheet; seen on a file not used for tuning). Not covered.

**Tests:** `PageFurnitureTest`

### Words split by a hyphen at a line end

**Can**
- A word is never cut by a line break: the rest of it moves up to the line with the hyphen (`high-` / `level` becomes `high-level`), also inside a link broken over lines (arXiv 1810.04805) and when Word draws a space after the hyphen. Tested by `HyphensTest`, `PdfLinksTest.wordSplitInsideALinkBrokenOverLinesIsJoined`.
- The hyphen goes when the document writes the word without it elsewhere (`learn-` / `ing`), when the rest is an ending that is no word (`surpris-` / `ing`, `represen-` / `tations`: -ing, -tion, -sion, -ity, -ment, -ness, -able, -ible, -ics), or when the word without it is in the English word list and the rest is not (`sur-` / `prisingly`, `computa-` / `tional`). The list is SCOWL size 35, 40,200 American and British words, 105 KB in the jar and about 0.5 MB on the heap once read (`words.txt.gz`, notice in `words-LICENSE.txt`). It stays when the document writes the word with it elsewhere (`multi-layer`), when it writes both forms, and in every other case. Tested by `HyphensTest`.
- A word the document shows otherwise counts as written too: the part of a compound of four letters or more (`encoder` of `auto-encoder` joins `en-` / `coder`, arXiv 1404.7828), and a word split before such an ending (`dimensional-` / `ity` joins `di-` / `mensionality` and `dimen-` / `sionality`, arXiv 1512.00567). Tested by `HyphensTest.wordsJoinedByTheirEndingAndPartsOfCompoundsAreWordsOfTheDocument`.
- On 270 hand-labelled breaks of the sample corpus (193 word breaks, 63 compounds, 14 unclear) 128 word breaks lose the hyphen (66%) and no compound does; OpenDataLoader 2.5.12 loses it in 24 compounds. Tested by `HyphensTest.labelledBreaksOfTheSampleCorpus` (a sample of each class).

**Cannot**
- A word break whose rest is an English word, or whose word is not in the list, keeps its hyphen: `in-side`, `for-ward`, `neurobio-logical`, names such as `Bel-mont`. Of the 193 labelled word breaks 65 keep it. A larger list does not help: SCOWL 70 joins as many (128), the 370,000-word dwyl list fewer (113), as more fragments count as words there. Not covered.

**Tests:** `HyphensTest`

### Links

**Can**
- Link annotations to web addresses become `[text](url)`, in tagged and untagged PDFs.
- A link box that ends inside a word does not split the word; internal links and unsafe addresses stay text.
- A link to itself becomes `<url>`; an address the document already writes in angle brackets (RFC 9562: `<https://...>`) stays as it is. Tested by `PdfLinksTest`.
- A link broken over lines of a paragraph is one link with a line break in its text (`[Distributed Computing\nEnvironment](url)`). Tested by `PdfLinksTest`.

**Cannot**
- Links inside PDF tables, on rotated pages and on rotated text stay text.
- A link box that starts inside a word (an address right after a digit of the same font, `1https://...`) does not start the link there: the word stays text. Not covered.
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
- A horizontal rule narrower than half the narrowest column is no row border: LaTeX draws the underscore of `conv2_x` as a rule, and arXiv 1512.03385 Table 1 has one row per stage as in the PDF. Tested by `RuledTablesTest.underscoreDrawnAsARuleDoesNotCutTheRow`.
- A caption right under the rows in their font is found, also when it ends up in their paragraph; `Table 2 shows` in a sentence is no caption.
- Left as text: rules without a vertical, a grid next to text of another column on the same lines, a cluster of more than 1,000 rules (untrusted input).
- Words keep their ligatures decomposed (`refinement`, not `re` + U+FB01 + `nement`), as in the rest of the text.

**Cannot**
- Tables without vertical rules (booktabs) or without a caption stay plain text, as do tables of many web-to-PDF tools.
- Columns without a rule between them share one cell (arXiv Table 10: 20 per-class columns are one cell, numbers in header order).
- Bracket glyphs of a matrix stay in the cells as `[ ]` (arXiv Table 1), and an underscore drawn as a rule is lost (`conv2 x`); all words are kept and in the right columns.
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
- A figure with at least three rows of mostly numbers, at most two font sizes apart, is a table drawn as a figure and keeps all its text: the header row `Config Size(MB) Lookup (ns) Model (ns)`, the group labels `Btree`, `Learned Index` and the column groups `Map Data Web Data Log-Normal Data` of arXiv 1712.01208 Figures 4 and 6. Rows of ticks of stacked plots are a plot's height apart, and boxes of network diagrams (`3x3 conv, 64`) and BERT tokens (`E1`) are no numbers. Tested by `FiguresTest.allTextOfATableDrawnAsAFigureStays`, `FiguresTest.axisTickRowsOfStackedPlotsAreNoTable`.
- Only what is visible counts as drawn: a placed picture's background clipped away under the next column does not join the columns.
- Never taken for a figure: a table of thin rules, a page background, a figure in the other column, `Fig. 4.` in the middle of a paragraph, a caption without graphics next to it.
- Left as they are (untrusted input): pages painting more than 10,000 boxes, or with more than 200 drawings or 50 captions.

**Cannot**
- A figure without such a caption keeps its text; rotated pages are skipped.
- Labels more than one caption font size above or below the drawing stay (arXiv Figure 3: the network names over its columns), unless a line is in a font at least 1.5 times the caption's and at most two of its own font sizes above the drawing. Tested by `FiguresTest`.
- In a table drawn as a figure with fewer than three rows of numbers, or with rows of words, short lines that are no row labels (column headers, labels spanning rows) are removed with the other labels.
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
- Leave footnote-like word endings (those become superscripts by the footnote-number rule under Paragraphs, not by this one), ordinary baseline numbers, standalone `10` powers without a numeric multiplier, dollar signs and existing Unicode unchanged in the tested cases.
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
- A bold paragraph without a heading style stays a paragraph, also when the author meant it as a section title: a false heading cuts a document for RAG in the wrong place, and court forms and glossaries use bold text for labels and terms (decided 2026-10-09). Tested by `DocxConverterTest.boldParagraphWithoutAHeadingStyleStaysAParagraph`.
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
- Internal and unsafe links stay text; an address shown as its own link text becomes `<url>`; brackets and parentheses are escaped. Tested by `DocxConverterTest.addressShownAsLinkTextIsAnAutolink`.

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

## HTML

Web pages saved as `.html` or `.htm`, based on jsoup. Developed on 13 public pages; measurements on that development set are in [docs/benchmark-html.md](docs/benchmark-html.md), a comparison on pages not used for development is still to come.

### Main content and page furniture

**Can**
- The only `<main>` (or `role="main"`) is the content; with none or several, the whole body. Tested by `HtmlConverterTest.theOnlyMainIsTheContent`, `twoMainsKeepTheWholeBody`.
- Left out: scripts, styles, embedded media, images, form controls, `nav`, hidden elements (`hidden`, `aria-hidden`, inline `display:none`), the landmark roles `navigation`, `banner`, `contentinfo`, `complementary`, `search`, "Skip to" links, and the `header`, `footer` and `aside` of the page. Those of an article, `main` or section stay (the title of a post, Sphinx footnotes). Tested by `navigationAndOtherPageFurnitureIsLeftOut`, `headerAndFooterOfAnArticleStay`, `sphinxPageKeepsFootnotesAndSourceLinkButNotPermalinks`.
- A form's text stays, only its controls go: ASP.NET wraps the whole page in one form. Tested by `navigationAndOtherPageFurnitureIsLeftOut`.
- Left out also: a list of at least three links to the page in other languages (`hreflang`), the `[edit]` links of MediaWiki and heading permalinks (`¶`, `§`, `#`). Tested by `languageSwitcherAndMediaWikiEditLinksAreLeftOut`, `sphinxPageKeepsFootnotesAndSourceLinkButNotPermalinks`.
- With no `h1`, the page `<title>` is the first heading. Tested by `titleIsTheHeadingWhenThereIsNoH1`.
- A heading below level 1 with nothing under it, before a heading of a higher level or at the end, is left out: the navigation under it is gone (gov.uk "Related content", GitHub "Repository files navigation"). Two headings of one level stay (the title of a gov.uk guide and of its part). Tested by `headingWithNothingUnderItIsLeftOut`.

**Cannot**
- Furniture made of plain `div`s stays: GitHub's sidebar (About, Topics, Stars), Wikipedia's categories, a "Help improve" box at the end of MDN. Only what is marked up as furniture goes; a page without `<main>` keeps everything else rather than risk losing text.
- A table of contents in `nav` is left out with the navigation (RFC HTML); its headings are in the text anyway.

### Text

**Can**
- Headings `h1` to `h6`, paragraphs, a line break (`<br>`) inside a paragraph, two line breaks between paragraphs (paulgraham.com). Tested by `headingsParagraphsAndLinks`, `quotesAndLineBreaks`, `twoLineBreaksEndAParagraph`.
- Custom elements of web apps (`<react-app>`, `<turbo-frame>`) and unknown tags are blocks: GitHub's README keeps its headings and lists. Tested by `customElementsAreBlocks`.
- White space, also no-break spaces (`&nbsp;`, U+202F), becomes one space; block syntax at the start of a line is escaped. Tested by `blockSyntaxAtLineStartIsEscapedAndSpacesAreNormal`.
- Text that a CommonMark renderer would take for HTML and hide is escaped: `List\<E>` (Javadoc, also when `E` is a link of its own), "the \<caption> element" (MDN), `\<!--`; `a < b` and `x<5` stay, code stays as it is, and literal angle brackets around an address (`\<<https://...>>`, RFC) render as "<address>". On the 13 pages no raw HTML is left. Tested by `textThatLooksLikeATagIsEscaped`.
- Digits in `<sub>` and `<sup>` become Unicode subscripts and superscripts (`0₁₆`, `10⁻³`); a reference `[2]` stays as it is. Tested by `digitsInSubscriptsAndSuperscriptsKeepTheirPlace`.
- `<pre>` is a fenced code block, longer than any run of backticks in it, with the language of its class or of the two wrappers above it: `language-x`, `lang-x`, `brush: x` (MDN), `highlight-x` (Sphinx), `highlight-source-x` (GitHub); inline `<code>` is a code span. Tested by `codeBlocksAndInlineCode`, `languageOfACodeBlockFromItsHighlighterClass`.
- Quotes become `>` blocks; definition lists and figure captions become paragraphs; images are left out. Tested by `quotesAndLineBreaks`, `imagesGoAndCaptionsAndDefinitionsStay`.
- Bold and italic are plain text, as in PDF and DOCX.
- A link right next to another element, which CSS lays out apart, gets a space where two words meet (GitHub topics `[css](...) [dom](...)`); brackets in elements around a footnote link stay tight (`[1]`), and a word split by tags stays whole (`Ex<b>am</b>ple`). Tested by `linksRightAfterAnotherElementAreSpacedApart`.

**Cannot**
- Formulas (MathML) are left as their text; the `alt` text of images is not kept.

### Lists and tables

**Can**
- Nested lists, four spaces per level; a numbered list starts at its `start`. Tested by `nestedAndNumberedLists`.
- A table of data is a Markdown table; a cell spanning columns (`colspan`) is followed by empty cells; `|` in a cell is escaped. Tested by `dataTableWithSpannedCells`.
- A table that lays out the page, with blocks in its cells or mostly empty cells (spacer images on Hacker News), is read as blocks. Tested by `layoutTableIsReadAsBlocks`, `tableOfMostlyEmptyCellsIsALayout`.

**Cannot**
- Cells spanning rows (`rowspan`) are not repeated in the rows below.

### Links

**Can**
- Links are `[text](url)`; only http, https and mailto, as in the other formats. The whole text of the link counts, also when it is part of the address (`Lib/json/__init__.py`). Spaces at the ends of the link text stay outside it. Tested by `headingsParagraphsAndLinks`, `unsafeLinksStayText`, `sphinxPageKeepsFootnotesAndSourceLinkButNotPermalinks`, `spaceAtTheEndOfALinkStays`.
- Relative links resolve against `<base href>`, else the address the caller read the page from (`convert(InputStream, fileName, URI)`), else the address the page gives itself: the canonical link or `og:url`. A relative `<base href>` is resolved against the caller's address; a user name and password in it do not reach the links. Only http(s) addresses count. Without one they stay text; links to anchors on the page always do. Tested by `relativeLinksUseTheAddressThePageGives`, `relativeLinksUseTheAddressTheCallerGives`.

**Cannot**
- `convert(Path)` takes no address: a saved page without a base, canonical link or `og:url` keeps its relative links as text; read it as a stream to pass one.

### Reading the file

**Can**
- The charset comes from a byte order mark or `<meta charset>`, else UTF-8; from a `Path` and from a stream, which is not closed. Tested by `charsetOfTheMetaTagIsUsed`, `foundByExtensionAndTheStreamIsNotClosed`.
- A page nested 100,000 elements deep converts without a stack overflow: deeper than 200 levels an element is read as its text. Tested by `deeplyNestedElementsDoNotOverflowTheStack`.
- Hostile pages take linear time and cannot blow up the output: tens of thousands of nested headers or language lists, a table widened by `colspan` to a million columns (a table of more than 100,000 cells, padding included, is read as blocks), lists and quotes nested thousands deep (deeper than 10 levels they are plain blocks). Tested by `hostilePagesTakeLinearTimeAndDoNotBlowUpTheOutput`.
- On the module path an application that requires only the core module gets jsoup too. Tested by `ModulePathTest`.
- An unreadable file fails with `DocumentConversionException`. Tested by `unreadableFileIsAConversionError`.

**Tests:** `HtmlConverterTest`, `ModulePathTest`

## TXT

Plain text files (`.txt`), without a parser dependency.

**Can**
- The lines as they are; blank lines between paragraphs, several of them one; line ends `
` and `` become `
`; spaces at the end of a line go. Tested by `TxtConverterTest.linesAndParagraphsStay`, `indentationAndTrailingSpaces`.
- Block syntax at the start of a line is escaped (`#`, `>`, code fences, rule lines), as in the other formats. Tested by `blockSyntaxAtLineStartIsEscaped`.
- Indentation stays: a table or code in the text keeps its columns. Tested by `indentationAndTrailingSpaces`.
- Text a renderer would take for HTML is escaped (`\<script>`, `List\<E>`, `\<!--`); an address in angle brackets (`<https://...>` in RFC text files) is escaped too and reads as text, not as a link: renderers differ on what is an autolink and what a tag. Tested by `textThatLooksLikeHtmlIsEscaped`.
- The charset comes from a byte order mark (UTF-8, UTF-16), else UTF-8 when the bytes are valid UTF-8, else windows-1252 (Notepad, Excel). Tested by `charsetsAndExtension`, `TextTest`.

**Cannot**
- Structure is not guessed: underlined titles, numbered sections and lists of a text file stay text (`- item` lines are Markdown list items anyway).
- A line indented by four spaces is a code block to a Markdown renderer.
- Other legacy charsets (windows-1251, Shift JIS) are read as windows-1252.

**Tests:** `TxtConverterTest`, `TextTest`, `ModulePathTest`

## CSV

CSV and TSV files (`.csv`, `.tsv`) as a Markdown table, without a parser dependency.

**Can**
- The first row is the header. Tested by `CsvConverterTest.firstRowIsTheHeader`.
- Fields follow RFC 4180: quoted fields hold the separator, line breaks (a space in the cell) and doubled quotes; `
` and `
` line ends. Spaces before an opening quote are allowed (`"Month", "1958"`). Tested by `quotedFieldsAfterRfc4180`, `spacesBeforeAnOpeningQuote`.
- A `.tsv` is separated by tabs; in a `.csv` the separator is the one of comma, semicolon (Excel in much of Europe) and tab the first row has most of. Tested by `semicolonAndTabSeparators`.
- The header is as wide as the widest row; a shorter row is not padded, a renderer adds its empty cells. Blank lines are no rows. Tested by `rowsOfOtherLengthsAndEmptyLines`.
- `|` in a cell is escaped, with the backslashes right before it doubled (else GFM would end the cell there and shift the columns; the same in the tables of PDF, DOCX and HTML), and text that looks like an HTML tag (`<b>`). Tested by `cellTextCannotBreakTheTable`, `MarkdownTest.tableCellTextCannotEndOrSplitItsCell`.
- A hostile file (a million separators in one row, an unclosed quote) takes linear time and does not blow up the output. Tested by `hostileFileTakesLinearTimeAndDoesNotBlowUp`.
- The charset as for TXT. Tested by `charsetsAndExtensions`.

**Cannot**
- A file without a header row gets its first data row as the header.
- A separator other than comma, semicolon and tab (`|`, spaces) is not found; the rows stay one cell each.
- Numbers are not aligned and types are not recognized; a very large file becomes a very large table (the size limit applies).

**Tests:** `CsvConverterTest`, `ModulePathTest`

## XLSX

Excel workbooks (`.xlsx`, Excel 2007+), with Apache POI.

**Can**
- Each visible sheet with text is a Markdown table, its first row with text the header. With more than one such sheet, each table follows a `#` heading with the sheet name; hidden and empty sheets are left out. Tested by `XlsxConverterTest.oneSheetIsATableWithShownValues`, `severalSheetsGetTheirNamesAsHeadingsAndHiddenOrEmptyOnesAreLeftOut`.
- A cell is the text Excel shows: number and date formats applied (US English, so the output does not depend on the machine), a formula the value Excel saved for it. Tested by `oneSheetIsATableWithShownValues`.
- Rows and columns without text in the whole sheet are left out. `|` in a cell is escaped, a line break becomes a space, text that looks like an HTML tag is escaped. Tested by `cellTextCannotBreakTheTable`.
- Only the cells the file holds are read: a cell in the last column (`XFD1`) adds one column, not 16,000. A table that would be more than a million cells and ten times more cells than have text (a few cells far apart in a small file) is rejected with `DocumentTooLargeException`. Tested by `cellsFarApartDoNotBlowUpTheTable`.
- Title, creator and language of the core properties as metadata. Tested by `metadataComesFromTheCoreProperties`.
- `Path` and stream give the same Markdown, the stream is not closed; an old `.xls`, a password-protected or a damaged file is a `DocumentConversionException`. Tested by `fileAndStreamGiveTheSameAndTheStreamIsNotClosed`, `oldXlsAndDamagedFilesAreConversionErrors`.

**Cannot**
- Merged cells are not spread over the columns they span; charts, pictures, comments and cell colours are left out.
- Formulas are not calculated again: in a file written by a program that does not calculate them, a formula cell shows no value or a wrong one. Not covered.
- The whole workbook is loaded into memory: a big sheet needs many times the file size (the size limit applies to the file only). `.xls`, `.xlsm` and `.xlsb` are not read.
- A sheet without a header row gets its first row as the header.

**Tests:** `XlsxConverterTest`, `ModulePathTest`
