# Benchmark: CastToMarkdown vs OpenDataLoader PDF

Measured on 2026-10-08, recounted on 2026-10-09 on 12 files; words recounted again after running headers stopped taking the column headings of tables (Fed Z.1, a file of the tuning corpus). The numbers in the README section "How it compares" come from here.

## What was compared

- **CastToMarkdown** 0.2.0-SNAPSHOT (PDFBox 3.0.8; 0.1.0 gave the same numbers except 97.3 % of the words kept; first measured on `main` at `3650529`, which gave the same words, links and hyphens on every file, one heading less on the 12 files and 10 instead of 36 on the Typst file): `CastToMarkdown.create().convert(path).markdown()`.
- **OpenDataLoader PDF** 2.5.12 (`org.opendataloader:opendataloader-pdf-core`): `OpenDataLoaderPDF.processFile(path, config)` with `generateMarkdown=true`, `generateJSON=false`, `imageOutput=off` and the defaults for the rest (`hybrid=off`, `readingOrder=xycut`, `tableMethod=default`, `includeHeaderFooter=false`). A second run sets `useStructTree=true`.
- Windows 11, OpenJDK 21.0.12, each library in its own JVM (they need different PDFBox versions). Every file is converted twice in one JVM, and the second pass is timed.

## Files

14 public PDFs from different generators. None of them was used to tune CastToMarkdown, and none was changed for it before the first numbers were taken. After that two of them were used to tune it, so they are left out of the totals: the Typst file for the headings of tagged PDFs, and chrome-python-json for lines drawn out of order (commit `40f38af` names its section headings). The README and the table below count 12 files. Their rows in the per-file table are kept. None is a scan. The files are not in this repository; download them from the links. Three of them are replaced at their address from time to time, so a later download may differ.

| File | Generator | Pages | Source |
|---|---|---|---|
| arxiv-math-0211159-perelman-math | dvips + Ghostscript, mathematics, one column | 39 | https://arxiv.org/pdf/math/0211159 |
| arxiv-1304.3061-vqe-revtex | pdfTeX, revtex, two columns | 10 | https://arxiv.org/pdf/1304.3061 |
| arxiv-1603.02754-xgboost-acm | pdfTeX, ACM sigconf, two columns | 13 | https://arxiv.org/pdf/1603.02754 |
| arxiv-1801.00862-nisq-quantph | pdfTeX, quantumarticle, one column | 20 | https://arxiv.org/pdf/1801.00862 |
| arxiv-1602.03837-ligo-distiller | Arbortext + Acrobat Distiller (journal PDF) | 16 | https://arxiv.org/pdf/1602.03837 |
| eu-key-figures-2014-2019-indesign | InDesign CC 13.1 | 14 | https://commission.europa.eu/document/download/2acccc4c-ab4c-4055-bf70-e2dd7490f006_en?filename=key_figures_for_the_eu_2014-2019_en.pdf |
| govuk-cycle-to-work-guidance-word | Word 2016 | 24 | https://assets.publishing.service.gov.uk/media/5dc9475440f0b64251080457/cycle-to-work-guidance.pdf |
| libreoffice-custom-shape-tutorial | LibreOffice 7.3 | 132 | https://documentation.libreoffice.org/assets/Uploads/Documentation/en/Tutorials/CustomShapes7/Custom-Shape-Tutorial.pdf |
| gdocs-chromium-win32k-lockdown | Google Docs | 9 | https://docs.google.com/document/d/1gJDlk-9xkh6_8M_awrczWCaUuyr0Zd2TKjNBCiPO_G4/export?format=pdf |
| chrome-python-json | Headless Chrome 155, a printed web page | 12 | `chrome --headless --print-to-pdf` of https://docs.python.org/3/library/json.html |
| rfc9457-weasyprint | xml2rfc + WeasyPrint 56.1 | 16 | https://www.rfc-editor.org/rfc/rfc9457.pdf |
| typst-oderso-documentation | Typst 0.15.1, tagged | 19 | https://github.com/dhbw-typst/oderso-template/releases/latest/download/documentation.pdf (changes with each release) |
| bls-empsit | XPP + Acrobat, statistical tables | 39 | https://www.bls.gov/news.release/pdf/empsit.pdf (monthly; refuses plain `curl`, use a browser) |
| fed-h41-aspose | Word + Aspose.Words, statistical tables | 11 | https://www.federalreserve.gov/releases/h41/current/h41.pdf (weekly) |

## How each number was taken

- **Words kept:** the words of `pdftotext -raw` (Poppler) of the PDF, compared as a bag of lower-case words with the words of the Markdown (link targets removed, ligatures expanded, NFKC). A word of the reference that the Markdown lacks counts as missing. Running headers and page numbers that a library removes on purpose count as missing too, so 100 % is not the goal. `pdftotext` keeps both halves of a word split at a line end, so joining such a word counts as missing as well.
- **Headings:** the entries of the PDF outline (bookmarks), 8 files have one. An entry is found when a Markdown heading (`#`) has the same text, ignoring section numbers, case, punctuation and accents. A Markdown heading that matches no entry is extra; the document title and "Abstract" count as extra, so compare the extra counts with each other only.
- **Links:** `[text](http...)` and `<http...>` in the Markdown.
- **Hyphens:** every word that `pdftotext` shows split by a hyphen at a line end (524), looked up in each output: joined without the hyphen, joined with it, or still broken. The cases where the outputs differ (46) were labelled by hand as word break, compound or unclear.
- **Tables:** the number of Markdown tables (a `|---|` separator row), checked by eye on the files where the counts differ.
- **Time:** wall time of the second pass over the 12 files, both libraries run one after the other on the same machine.

## Results

| | CastToMarkdown | OpenDataLoader | OpenDataLoader, `useStructTree` |
|---|---|---|---|
| Words kept (143,012 reference words) | 97.5 % | 96.6 % | 96.5 % |
| Outline headings found (312 in 7 files) / extra | 269 / 25 | 237 / 89 | 255 / 84 |
| Links | 362 | 0 | 0 |
| Word breaks left with their hyphen, of the 46 cases where the outputs differ | 25 | 0 | |
| Compounds that lost their hyphen, of the same 46 | 0 | 21 | |
| Time, 12 files | 1.9 s | 3.4 s | |

With the two left-out files (14 files): words 97.4 % / 96.5 % / 95.9 % of 149,688; headings 305 / 82, 252 / 110, 353 / 96 of 420; links 457, 0, 0; time 2.1 s and 3.6 s. Of the 46 hyphen cases none is in these two files.

Per file:

| File | Words kept: ours / ODL / ODL struct (%) | Outline headings: entries; found / extra for ours, ODL, ODL struct | Markdown tables: ours / ODL / ODL struct | Links: ours / ODL |
|---|---|---|---|---|
| arxiv-1304.3061-vqe-revtex | 93.5 / 93.0 / 93.0 | 4; 1/0, 1/12, 1/12 | 0 / 10 / 10 | 57 / 0 |
| arxiv-1602.03837-ligo-distiller | 95.2 / 94.9 / 94.9 | no outline | 0 / 3 / 3 | 144 / 0 |
| arxiv-1603.02754-xgboost-acm | 94.9 / 95.5 / 95.5 | 29; 23/9, 19/16, 19/16 | 1 / 39 / 39 | 2 / 0 |
| arxiv-1801.00862-nisq-quantph | 96.8 / 96.6 / 96.6 | 23; 23/3, 17/5, 17/5 | 0 / 0 / 0 | 46 / 0 |
| arxiv-math-0211159-perelman-math | 97.3 / 95.5 / 95.5 | no outline | 0 / 0 / 0 | 0 / 0 |
| bls-empsit | 99.9 / 99.4 / 99.0 | no outline | 0 / 32 / 0 | 0 / 0 |
| chrome-python-json (not in the totals) | 92.2 / 92.1 / 73.6 | no outline | 2 / 2 / 2 | 87 / 0 |
| eu-key-figures-2014-2019-indesign | 94.5 / 96.5 / 99.5 | no outline | 0 / 8 / 0 | 0 / 0 |
| fed-h41-aspose | 99.2 / 99.5 / 99.5 | no outline | 0 / 11 / 11 | 0 / 0 |
| gdocs-chromium-win32k-lockdown | 99.9 / 99.9 / 99.9 | 30; 28/0, 28/1, 28/0 | 1 / 1 / 1 | 17 / 0 |
| govuk-cycle-to-work-guidance-word | 99.6 / 99.4 / 96.7 | 43; 42/6, 24/5, 42/1 | 4 / 8 / 4 | 19 / 0 |
| libreoffice-custom-shape-tutorial | 98.5 / 96.2 / 96.2 | 152; 124/5, 122/42, 122/42 | 9 / 36 / 36 | 38 / 0 |
| rfc9457-weasyprint | 95.2 / 94.8 / 94.8 | 31; 28/2, 26/8, 26/8 | 0 / 0 / 0 | 45 / 0 |
| typst-oderso-documentation (not in the totals) | 100.0 / 99.8 / 99.8 | 108; 36/57, 15/21, 98/12 | 4 / 1 / 4 | 8 / 0 |

## What the numbers do not show

- **Tables.** OpenDataLoader's 32 tables in bls-empsit and 11 in fed-h41-aspose are real statistical tables; CastToMarkdown writes them as plain text. In the arXiv papers most of OpenDataLoader's tables are figures and algorithm boxes turned into empty or one-column tables (39 in the XGBoost paper, which has about six real ones; CastToMarkdown finds one of them).
- **Noise.** OpenDataLoader writes `<br>` inside table cells (4,353 in bls-empsit) and HTML entities in the text (`&lt;` and `&gt;`: 229 in the Perelman paper, 528 in the LibreOffice tutorial). These count as extra words, not as missing ones, so the "words kept" row does not lower OpenDataLoader for them.
- **Figures and formulas.** CastToMarkdown breaks formulas and text drawn rotated or letter by letter inside figures into many short paragraphs (`qu` / `an` / `tu` / `m` in the revtex paper); the cover title of the EU document comes out as nine short paragraphs.
- **Tagged PDFs.** Both libraries take the headings of the tagged Typst PDF from its structure tree, OpenDataLoader with `useStructTree=true` (98 of 108 found). CastToMarkdown finds 36 by this count (10 before it read the structure tree), but most of its 57 extra headings are outline entries too: it writes the parameter type after the name as in the PDF (`lang str`), and the outline has the name alone (`lang`).

## The same comparison on the files used for tuning

CastToMarkdown was tuned on 21 other PDFs (arXiv, NIST, an RFC, Word, InDesign, LibreOffice and Chrome exports). On those its lead is larger: 287 of 298 outline headings with 58 extra against 225 with 193, and 1.8 times the speed. On words kept OpenDataLoader was ahead there (96.6 % against 95.6 %; a word joined at a line-end hyphen counts as missing), mostly because CastToMarkdown leaves out the text inside captioned figures, which those papers have more of. These numbers are not in the README for that reason.
