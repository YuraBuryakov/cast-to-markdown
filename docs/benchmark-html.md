# HTML module: development set

Measured on 2026-10-09 on the 13 pages the HTML module was **developed on** (a development set, not a holdout: every rule of the module was written or checked against these pages). These numbers are not a fair comparison and are not in the README; a measurement on pages not used for development follows.

## Pages

Saved with `curl` on 2026-10-09 (the HTML as the server sends it, no scripts run): Wikipedia "Universally unique identifier", Python `json` documentation (Sphinx), MDN `<table>`, gov.uk "Register for VAT", GitHub `jhy/jsoup`, Martin Fowler "Microservices", Paul Graham "Beating the Averages", Hacker News item 1, Spring Boot `SpringApplication`, RFC 9562 (HTML), Java 21 Javadoc `java.util.List`, jsoup cookbook "selector syntax", NASA "James Webb Space Telescope".

## Method

- **Main text kept:** for each page, the text of the element that holds its content (chosen by hand: `#mw-content-text`, `div[role=main]`, `article.markdown-body`, ...) is the reference; the share of its words (lower case, as a bag) found in the Markdown, link targets removed.
- **From outside the main text:** the share of the Markdown's words that the reference does not have.
- Compared with copy-down 1.1 (the Java port of Turndown), which converts the whole page.

## Results

| | CastToMarkdown | copy-down 1.1 |
|---|---|---|
| Words of the main text kept | 98.5 % | 99.5 % |
| Words of the output from outside the main text | 1 % | 27 % |
| Raw HTML a CommonMark renderer would hide | 0 | not measured |

What we lose is mostly left out on purpose: MediaWiki `[edit]` links, NASA's search and sort controls, Hacker News' parent/root links, the table of contents of the RFC (in `nav`), and subscripts written as `₂` that the word count does not see.
