# Benchmark: HTML module vs copy-down

Two measurements: the 13 pages the module was developed on, and 10 pages not used for development (the holdout). The numbers in the README section "HTML" come from the holdout, at the end.

## Development set

Measured on 2026-10-09 on the 13 pages the HTML module was **developed on** (a development set, not a holdout: every rule of the module was written or checked against these pages). These numbers are not a fair comparison and are not in the README; a measurement on pages not used for development follows.

### Pages

Saved with `curl` on 2026-10-09 (the HTML as the server sends it, no scripts run): Wikipedia "Universally unique identifier", Python `json` documentation (Sphinx), MDN `<table>`, gov.uk "Register for VAT", GitHub `jhy/jsoup`, Martin Fowler "Microservices", Paul Graham "Beating the Averages", Hacker News item 1, Spring Boot `SpringApplication`, RFC 9562 (HTML), Java 21 Javadoc `java.util.List`, jsoup cookbook "selector syntax", NASA "James Webb Space Telescope".

### Method

- **Main text kept:** for each page, the text of the element that holds its content (chosen by hand: `#mw-content-text`, `div[role=main]`, `article.markdown-body`, ...) is the reference; the share of its words (lower case, as a bag) found in the Markdown, link targets removed.
- **From outside the main text:** the share of the Markdown's words that the reference does not have.
- Compared with copy-down 1.1 (the Java port of Turndown), which converts the whole page.

### Results

| | CastToMarkdown | copy-down 1.1 |
|---|---|---|
| Words of the main text kept | 98.5 % | 99.5 % |
| Words of the output from outside the main text | 1 % | 27 % |
| Raw HTML a CommonMark renderer would hide | 0 | not measured |

What we lose is mostly left out on purpose: MediaWiki `[edit]` links, NASA's search and sort controls, Hacker News' parent/root links, the table of contents of the RFC (in `nav`), and subscripts written as `₂` that the word count does not see.

## Holdout: 10 pages not used for development

Measured on 2026-10-09 with `main` at `3dbd972`, after the module was merged; nothing was changed for these pages. One page per site type, saved with `curl` (the HTML as the server sends it, no scripts run):

| Page | Site type | Source |
|---|---|---|
| rtd-requests-quickstart | Sphinx, Read the Docs | https://requests.readthedocs.io/en/latest/user/quickstart/ |
| mkdocs-writing-docs | MkDocs | https://www.mkdocs.org/user-guide/writing-your-docs/ |
| docusaurus-markdown | Docusaurus | https://docusaurus.io/docs/markdown-features |
| javadoc-commons-stringutils | Javadoc | https://commons.apache.org/proper/commons-lang/apidocs/org/apache/commons/lang3/StringUtils.html |
| blog-jvns-git-config | blog | https://jvns.ca/blog/2024/02/16/popular-git-config-options/ |
| archwiki-systemd | MediaWiki, not Wikipedia | https://wiki.archlinux.org/title/Systemd |
| gov-usa-passport | government site | https://www.usa.gov/passport |
| github-issue-springboot | GitHub (a pull request) | https://github.com/spring-projects/spring-boot/issues/1 |
| fed-h15-table | data table | https://www.federalreserve.gov/releases/h15/ |
| news-npr-article | news | https://www.npr.org/2026/10/08/nx-s1-5995576/openai-russia-iran-influence-operations-chatgpt |

Stack Overflow refused the download (403).

The reference for the main text was chosen before looking at any output: `div[role=main]` (Read the Docs, MkDocs), `article` (Docusaurus, blog), `#mw-content-text`, `#content` (Fed), `#storytext` (NPR) and `main` (Javadoc, usa.gov, GitHub). `main` is what CastToMarkdown takes as the content, which favours it on those three pages; on GitHub and usa.gov it also holds navigation and hidden interface text that CastToMarkdown leaves out, which counts as lost words.

| Page | Main text kept: ours / copy-down | Output from outside the main text: ours / copy-down |
|---|---|---|
| archwiki-systemd | 99.6 % / 98.5 % | 0 % / 18 % |
| blog-jvns-git-config | 98.6 % / 99.4 % | 0 % / 9 % |
| docusaurus-markdown | 99.1 % / 99.2 % | 2 % / 47 % |
| fed-h15-table | 100.0 % / 100.0 % | 1 % / 60 % |
| github-issue-springboot | 83.0 % / 99.9 % | 0 % / 50 % |
| gov-usa-passport | 69.8 % / 100.0 % | 0 % / 67 % |
| javadoc-commons-stringutils | 99.7 % / 99.8 % | 0 % / 13 % |
| mkdocs-writing-docs | 100.0 % / 99.9 % | 1 % / 8 % |
| news-npr-article | 99.7 % / 100.0 % | 10 % / 39 % |
| rtd-requests-quickstart | 99.3 % / 99.2 % | 2 % / 12 % |
| **Mean per page** | **95.0 % / 99.6 %** | **1.5 % / 32.8 %** |
| All words together | 99.1 % / 99.6 % | 0 % / 19 % |

| | CastToMarkdown | copy-down 1.1 |
|---|---|---|
| Raw HTML a CommonMark renderer would hide (`html_inline` and `html_block` tokens of markdown-it outside code) | 0 | 8 |
| Links (`[text](http...)`, `<http...>`) | 1,042 | 1,755, plus relative paths |
| Javadoc commons-lang: links of any kind | 759 | 1,986 (1,311 absolute, 675 relative paths) |
| Links next to each other without a space (`](...)[`) | 26 | 39 |
| Time, 10 pages | 0.15 s (second, warm pass) | 2.9 s (one cold pass) |

Compared with the development set, the share of the output from outside the main text is as low (1.5 % against 1 %). The main text kept is lower in the mean per page because of the two pages where the reference `main` holds navigation (GitHub 83 %, usa.gov 70 %); without them the mean is 99.5 %. copy-down keeps more of the main text, as it keeps the whole page. CastToMarkdown has fewer links because a page saved without its address (`<base>`, canonical link, `og:url`) keeps its relative links as text.
