# Instructions for AI assistants

## When a session starts

Do these steps before writing any code:

1. **Load context.** Read this file. If the Obsidian vault is reachable, read `CastToMarkdown — Project Context` (especially sections 22–26) and `Obsidian — AI Working Rules`. Note open questions such as `Q-SDK-*`.
2. **Check the repository.** Run `git status` and `git log --oneline -10` if git is initialised. Look at what modules, classes and tests exist.
3. **Check the build.** Run `mvn verify`. If it fails, report the failure first; do not start new work on a red build.
4. **Compare repo with plan.** Find what the Project Context plans but the code does not have yet, and any place where the code contradicts a recorded decision.
5. **Report to the author, briefly:**
   - current state (what exists, build green/red);
   - decisions still open that block the next step;
   - 1–3 proposed next steps, each small enough for one commit.
6. **Wait for the author to choose.** Do not start implementing until the author picks a step.

Reply in the language the author writes in.

### Current next step

PDF works: paragraphs, headings (fonts, bold TeX fonts without a weight, and the structure tree of tagged PDFs), bullet lists, tables of tagged PDFs (structure tree + MCIDs) and captioned ruled tables of untagged PDFs (grid of rules + `Table N.` caption), text inside captioned figures left out, headers/footers removed, lines drawn late put back in reading order, web links as `[text](url)` (link annotations, tagged or not), scans rejected, `Path` and `InputStream` input, size limit. DOCX works: style headings, lists with numbering, tables, footnotes, external links. HTML works (0.2.0-SNAPSHOT, jsoup): main content of web pages without furniture, headings, lists, tables, code, quotes, links resolved against the page's own address; design in the Obsidian note "CastToMarkdown - HTML Module (design)". Modules: `core`, `pdf`, `docx`, `html`. Release 0.1.0: the code and docs are ready (README numbers recounted on 2026-10-09 for `feat-numbered-subheadings`); the author still sets up the GPG key and the Central token, then follows `RELEASING.md`. The `commons.math3` warning comes from POI's own module descriptor and does not block publishing (see `RELEASING.md`).

Known limitations: Markdown escaping covers block syntax at line start only (`#`, `>`, fences, rule lines), not list markers or inline syntax; tables of untagged PDFs without vertical rules or caption (WeasyPrint, RFCs on a page background) are plain text; merged table cells are not spread over columns; links inside PDF tables and DOCX HYPERLINK fields stay plain text. Findings and open questions are in the Obsidian notes "CastToMarkdown - Experiment 01/02".

### During the session

- One small step at a time; run the tests after each step.
- Suggest a commit message after each logical step. Commit only when the author says so.
- `FEATURES.md` is the list of what each feature can and cannot do. A change to a feature's behaviour updates its section in the same commit; a "Can" line names the test that checks it, or says it is not covered.

### At the end of the session

If a decision was made, a question was answered or something important was learned, update the relevant Obsidian note following `Obsidian — AI Working Rules`. Change question statuses, record *why* a decision was made, and link related notes.
If the vault is not reachable, give the author a short summary to save.

## Read first

The source of truth for this project lives in the author's Obsidian vault (MiniBrain), not in this repository:

```text
C:\Users\yurab\Dropbox\Obsidian\MiniBrain\
├── 00 Meta\Obsidian — AI Working Rules.md
├── 30 Projects\Document Preparation\CastToMarkdown — Project Context.md      ← main context
├── 30 Projects\Document Preparation\CastToMarkdown — Product Scope & Value.md
└── 40 Knowledge\Java\Java Library Design — Rules & Practices.md
```

If you can reach the vault, read the Project Context before proposing architecture or code. If not, follow the summary below.

## Fixed decisions

| Topic | Decision |
|---|---|
| Name | CastToMarkdown (repository `cast-to-markdown`) |
| Build | Maven, multi-module |
| Java | Minimum Java 17 (`maven.compiler.release=17`) |
| groupId | `io.github.yuraburyakov` |
| Base package | `io.github.yuraburyakov.casttomarkdown` |
| License | Apache 2.0 |
| Structure | Maven modules: `core` (API, no parsers), one module per format (`pdf`, ...). Converters are found with `ServiceLoader` (`provides` in `module-info` + `META-INF/services`) |

### Public API (iteration 1)

| Topic | Decision |
|---|---|
| Entry point | Instance: `CastToMarkdown.create().convert(path)`; immutable, thread-safe. No static `convert` yet |
| Result | `public final class PreparedDocument` (not a record), package-private constructor, only `markdown()` for now |
| Input | `convert(Path)` and `convert(InputStream, String fileName)`. The caller owns the stream: it is read to the end, never closed (also on error). The file name's extension selects the format |
| Errors | Unchecked `DocumentConversionException`; subclasses `UnsupportedFormatException` (format, scanned PDF) and `DocumentTooLargeException` |
| Settings | `CastToMarkdown.builder()...build()`; `create()` = defaults. Only `maxDocumentSize` (default 100 MiB) so far |
| Converters | Interface `internal.DocumentConverter` in `core`, exported only to the format modules (`exports ... to`). Each format module has one package with exactly one public class, the rest package-private. `CastToMarkdown` picks the converter by file extension. No public SPI |

Not decided yet: metadata, warnings, logging, Markdown escaping beyond `#` at line start.

## How to work here

1. Small iterations: one class or one small slice at a time, then a small commit. The author reviews every change.
2. For non-trivial design choices, offer 2–3 options with trade-offs and failure modes. The author decides.
3. Separate facts, hypotheses and decisions. Do not turn a guess into architecture.
4. Research before inventing: check PDFBox/POI/jsoup capabilities and existing projects first.
5. Prefer tests or experiments when the answer depends on real parser behaviour.
6. No abstractions just to demonstrate patterns. No interface without a real reason.
7. Do not refactor unrelated code.

## Library rules (short version)

- Keep the public API small; everything else package-private where possible.
- Public types do not expose parser types. The root package is the only API; `internal` is exported only to format modules and may change in any version.
- No framework dependencies (no Spring).
- Do not leak PDFBox/POI types through the public API.
- Every new dependency needs a reason: is it a capability or a convenience?
- Be explicit about resource ownership: do not close an `InputStream` the caller passed in unless documented.
- Immutable public result objects; predictable `null` policy.
- No `System.out`, no global mutable state.
- Deterministic output for the same input, version and configuration.
- Parsers handle untrusted input: think about huge, malformed and encrypted documents.
