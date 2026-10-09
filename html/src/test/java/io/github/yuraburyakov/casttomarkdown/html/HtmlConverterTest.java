package io.github.yuraburyakov.casttomarkdown.html;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlConverterTest {

    private final HtmlConverter converter = new HtmlConverter();

    @TempDir
    Path dir;

    @Test
    void headingsParagraphsAndLinks() {
        assertThat(convert("<h1>Title</h1><p>Text <b>bold</b> and <a href='https://x.org/a'>a link</a>.</p>"
                + "<h2>Part</h2><p>More   text\n here.</p>"))
                .isEqualTo("# Title\n\nText bold and [a link](https://x.org/a).\n\n## Part\n\nMore text here.\n");
    }

    @Test
    void navigationAndOtherPageFurnitureIsLeftOut() {
        assertThat(convert("<body><a href='#main'>Skip to main content</a>"
                + "<header><a href='/'>Site</a><nav><a href='/a'>Home</a></nav></header>"
                + "<div role='navigation'>Menu</div><aside>Related</aside>"
                + "<div id='main'><h1>Post</h1><p>Body.</p>"
                + "<form><label>Search</label><input value='q'><button>Go</button></form></div>"
                + "<div hidden>Secret</div><div aria-hidden='true'>Icon</div><p style='display: none'>Gone</p>"
                + "<script>var x = 1;</script><style>p {}</style><noscript>Enable JS</noscript>"
                + "<footer>© Site 2026</footer></body>"))
                .isEqualTo("# Post\n\nBody.\n\nSearch\n");
    }

    @Test
    void headerAndFooterOfAnArticleStay() {
        assertThat(convert("<header>Site</header><article><header><h1>Post</h1></header><p>Body.</p>"
                + "<footer>By Ann</footer></article><footer>© Site</footer>"))
                .isEqualTo("# Post\n\nBody.\n\nBy Ann\n");
    }

    @Test
    void theOnlyMainIsTheContent() {
        assertThat(convert("<div>Promo banner</div><main><h1>Docs</h1><p>Text.</p></main><div>Newsletter</div>"))
                .isEqualTo("# Docs\n\nText.\n");
        assertThat(convert("<div>Promo</div><div role='main'><p>Text.</p></div>")).isEqualTo("Text.\n");
    }

    @Test
    void customElementsAreBlocks() {
        // GitHub wraps its README in <react-app> and <turbo-frame>
        assertThat(convert("<turbo-frame><react-app><h1>jsoup</h1><p>Java HTML parser.</p><ul><li>a</li></ul>"
                + "</react-app></turbo-frame><p>Text <span>in a span</span>.</p>"))
                .isEqualTo("# jsoup\n\nJava HTML parser.\n\n- a\n\nText in a span.\n");
    }

    @Test
    void languageSwitcherAndMediaWikiEditLinksAreLeftOut() {
        // Wikipedia: the other languages of the article, as links with hreflang, and an [edit] link at each heading
        assertThat(convert("<main><h1>UUID</h1><ul><li><a href='https://de.wikipedia.org/wiki/UUID' hreflang='de'>Deutsch</a></li>"
                + "<li><a href='https://fr.wikipedia.org/wiki/UUID' hreflang='fr'>Français</a></li>"
                + "<li><a href='https://ru.wikipedia.org/wiki/UUID' hreflang='ru'>Русский</a></li></ul>"
                + "<h2>History<span class='mw-editsection'>[<a href='/w/index.php?action=edit'>edit</a>]</span></h2>"
                + "<p>Text in <a href='https://www.gov.uk/cy' hreflang='cy'>Welsh</a>.</p></main>"))
                .isEqualTo("# UUID\n\n## History\n\nText in [Welsh](https://www.gov.uk/cy).\n");
    }

    @Test
    void digitsInSubscriptsAndSuperscriptsKeepTheirPlace() {
        // Wikipedia: "0xxx<sub>2</sub>, 0<sub>16</sub>"; a reference [2] in <sup> stays as it is
        assertThat(convert("<p>0xxx<sub>2</sub>, 0<sub>16</sub>, 10<sup>-3</sup>, x<sup>n</sup>,"
                + " text<sup><a href='#cite-2'>[2]</a></sup>.</p>"))
                .isEqualTo("0xxx₂, 0₁₆, 10⁻³, xⁿ, text[2].\n");
    }

    @Test
    void sphinxPageKeepsFootnotesAndSourceLinkButNotPermalinks() {
        // Python docs: a "¶" link at each heading, footnotes in <aside> inside the content, the source as a link
        assertThat(convert("<aside>Site sidebar</aside><main><h1>json<a class='headerlink' href='#json'>¶</a></h1>"
                + "<p>Source code: <a href='https://github.com/python/cpython/tree/3.14/Lib/json/__init__.py'>"
                + "Lib/json/__init__.py</a></p><p>Text.</p><aside class='footnote'><p>1. A footnote.</p></aside></main>"))
                .isEqualTo("# json\n\nSource code: [Lib/json/__init__.py](https://github.com/python/cpython/tree/3.14/Lib/json/__init__.py)"
                        + "\n\nText.\n\n1. A footnote.\n");
    }

    @Test
    void twoMainsKeepTheWholeBody() {
        assertThat(convert("<p>Intro</p><main><p>One</p></main><main><p>Two</p></main>"))
                .isEqualTo("Intro\n\nOne\n\nTwo\n");
    }

    @Test
    void nestedAndNumberedLists() {
        assertThat(convert("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul><ol start='3'><li>x</li><li>y</li></ol>"))
                .isEqualTo("- a\n    - b\n- c\n\n3. x\n4. y\n");
    }

    @Test
    void dataTableWithSpannedCells() {
        assertThat(convert("<table><tr><th>A</th><th>B</th></tr><tr><td colspan='2'>wide</td></tr>"
                + "<tr><td>1|2</td><td><a href='https://x.org'>three</a></td></tr></table>"))
                .isEqualTo("| A | B |\n| --- | --- |\n| wide |  |\n| 1\\|2 | [three](https://x.org) |\n");
    }

    @Test
    void layoutTableIsReadAsBlocks() {
        assertThat(convert("<table><tr><td><p>One</p><p>Two</p></td><td><ul><li>x</li></ul></td></tr></table>"))
                .isEqualTo("One\n\nTwo\n\n- x\n");
    }

    @Test
    void tableOfMostlyEmptyCellsIsALayout() {
        // Hacker News: each comment is a row of spacer cells (an image, gone) and the text
        assertThat(convert("<table><tr><td><img src='s.gif'></td><td></td><td>pg on Oct 9 | Is there anything?</td></tr>"
                + "</table>"))
                .isEqualTo("pg on Oct 9 | Is there anything?\n");
    }

    @Test
    void twoLineBreaksEndAParagraph() {
        // paulgraham.com: paragraphs are text separated by <br><br>
        assertThat(convert("<p>First line<br>goes on.<br><br>Second paragraph.<br> <br>Third.</p>"))
                .isEqualTo("First line\ngoes on.\n\nSecond paragraph.\n\nThird.\n");
    }

    @Test
    void codeBlocksAndInlineCode() {
        assertThat(convert("<p>Call <code>convert()</code> now.</p>"
                + "<pre><code class='language-java'>int a = 1;\n``` not the end\n  indented</code></pre>"))
                .isEqualTo("Call `convert()` now.\n\n````java\nint a = 1;\n``` not the end\n  indented\n````\n");
    }

    @Test
    void quotesAndLineBreaks() {
        assertThat(convert("<blockquote><p>Quote</p><p>Two<br>lines</p></blockquote>"))
                .isEqualTo("> Quote\n>\n> Two\n> lines\n");
    }

    @Test
    void relativeLinksUseTheAddressThePageGives() {
        String body = "<p><a href='/docs/intro'>Intro</a> <a href='#top'>Top</a></p>";
        assertThat(convert("<head><base href='https://a.org/x/'></head>" + body))
                .isEqualTo("[Intro](https://a.org/docs/intro) Top\n");
        assertThat(convert("<head><link rel='canonical' href='https://b.org/page'></head>" + body))
                .isEqualTo("[Intro](https://b.org/docs/intro) Top\n");
        assertThat(convert("<head><meta property='og:url' content='https://c.org/p'></head>" + body))
                .isEqualTo("[Intro](https://c.org/docs/intro) Top\n");
        assertThat(convert(body)).isEqualTo("Intro Top\n");
    }

    @Test
    void spaceAtTheEndOfALinkStays() {
        // xml2rfc: <a>A.1. </a><a>Example</a> in a heading
        assertThat(convert("<h3><a href='#s-a.1'>A.1. </a><a href='#n-ex'>Example</a></h3>"
                + "<p><a href='https://a.org'>one </a>two</p>"))
                .isEqualTo("### A.1. Example\n\n[one](https://a.org) two\n");
    }

    @Test
    void unsafeLinksStayText() {
        assertThat(convert("<p><a href='javascript:alert(1)'>Click</a> <a href='mailto:a@b.org'>Mail</a></p>"))
                .isEqualTo("Click [Mail](mailto:a@b.org)\n");
    }

    @Test
    void titleIsTheHeadingWhenThereIsNoH1() {
        assertThat(convert("<head><title> Page name </title></head><body><p>Text.</p></body>"))
                .isEqualTo("# Page name\n\nText.\n");
        assertThat(convert("<head><title>Site</title></head><body><h1>Post</h1></body>")).isEqualTo("# Post\n");
    }

    @Test
    void imagesGoAndCaptionsAndDefinitionsStay() {
        assertThat(convert("<figure><img src='a.png' alt='A chart'><figcaption>Figure 1. Sales</figcaption></figure>"
                + "<dl><dt>Term</dt><dd>Meaning</dd></dl>"))
                .isEqualTo("Figure 1. Sales\n\nTerm\n\nMeaning\n");
    }

    @Test
    void blockSyntaxAtLineStartIsEscapedAndSpacesAreNormal() {
        assertThat(convert("<p># not a heading</p><p>a&nbsp;b (the  <abbr>VAT</abbr>  threshold)</p>"))
                .isEqualTo("\\# not a heading\n\na b (the VAT threshold)\n");
    }

    @Test
    void deeplyNestedElementsDoNotOverflowTheStack() {
        String html = "<div>".repeat(100_000) + "deep" + "</div>".repeat(100_000);

        String markdown = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> convert(html));

        assertThat(markdown).isEqualTo("deep\n");
    }

    @Test
    void hostilePagesTakeLinearTimeAndDoNotBlowUpTheOutput() {
        String headers = "<header>".repeat(50_000) + "x" + "</header>".repeat(50_000);
        String languages = "<ul><li><a hreflang='de' href='https://a.org'>de</a><ul>".repeat(20_000) + "</ul></li></ul>".repeat(20_000);
        String wideRow = "<table><tr>" + "<td colspan='50'>c</td>".repeat(20_000) + "</tr>" + "<tr><td>r</td></tr>".repeat(5_000) + "</table>";
        String deepLists = "<ul><li>item".repeat(3_000) + "</li></ul>".repeat(3_000);
        String deepQuotes = "<blockquote><p>q</p>".repeat(3_000) + "</blockquote>".repeat(3_000);

        for (String html : List.of(headers, languages, wideRow, deepLists, deepQuotes)) {
            String markdown = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> convert(html));
            assertThat(markdown.length()).as(html.substring(0, 40)).isLessThan(20 * html.length());
        }
    }

    @Test
    void charsetOfTheMetaTagIsUsed() throws IOException {
        Charset cp1251 = Charset.forName("windows-1251");
        byte[] html = "<html><head><meta charset='windows-1251'></head><body><p>Привет</p></body></html>".getBytes(cp1251);
        Path file = dir.resolve("page.htm");
        Files.write(file, html);

        assertThat(converter.convert(file)).isEqualTo("Привет\n");
        assertThat(converter.convert(new ByteArrayInputStream(html), "page.html")).isEqualTo("Привет\n");
    }

    @Test
    void foundByExtensionAndTheStreamIsNotClosed() throws IOException {
        Path file = dir.resolve("page.HTM");
        Files.writeString(file, "<p>Hello</p>");
        assertThat(CastToMarkdown.create().convert(file).markdown()).isEqualTo("Hello\n");

        boolean[] closed = {false};
        InputStream stream = new ByteArrayInputStream("<p>Hi</p>".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        assertThat(CastToMarkdown.create().convert(stream, "a.html").markdown()).isEqualTo("Hi\n");
        assertThat(closed[0]).isFalse();
    }

    @Test
    void unreadableFileIsAConversionError() {
        assertThatThrownBy(() -> converter.convert(dir.resolve("missing.html")))
                .isInstanceOf(DocumentConversionException.class);
    }

    private String convert(String html) {
        return converter.convert(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), "page.html");
    }
}
