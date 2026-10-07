package io.github.yuraburyakov.casttomarkdown.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownTest {

    @Test
    void escapesHashAtLineStart() {
        // arXiv table text and RFC code comments start with "#".
        assertThat(Markdown.escape("# layers 1+2n")).isEqualTo("\\# layers 1+2n");
        assertThat(Markdown.escape("## two")).isEqualTo("\\## two");
        assertThat(Markdown.escape("  # indented")).isEqualTo("  \\# indented");
    }

    @Test
    void escapesBlockSyntaxThatWouldSwallowOrRestyleText() {
        assertThat(Markdown.escape("> not a quote")).isEqualTo("\\> not a quote");
        assertThat(Markdown.escape("```not code")).isEqualTo("\\```not code");
        assertThat(Markdown.escape("~~~ not code")).isEqualTo("\\~~~ not code");
        assertThat(Markdown.escape("---")).isEqualTo("\\---");
        assertThat(Markdown.escape("===")).isEqualTo("\\===");
        assertThat(Markdown.escape(" * * *")).isEqualTo(" \\* * *");
        assertThat(Markdown.escape("-")).isEqualTo("\\-");
    }

    @Test
    void escapesExactlyTheLinesCommonMarkReadsAsSyntax() {
        // checked with commonmark-java 0.24: alone and right after a line of text
        String[][] cases = {
            {"-", "\\-"}, {"--", "\\--"}, {"---", "\\---"}, {"- - -", "\\- - -"},
            {"*", "\\*"}, {"**", "**"}, {"***", "\\***"},
            {"_", "_"}, {"__", "__"}, {"___", "\\___"},
            {"=", "\\="}, {"==", "\\=="}, {"===", "\\==="},
            {"> text", "\\> text"}, {"# text", "\\# text"}, {"```text", "\\```text"}, {"~~~text", "\\~~~text"},
        };
        for (String[] c : cases) {
            assertThat(Markdown.escape(c[0])).as(c[0]).isEqualTo(c[1]);
        }
    }

    @Test
    void keepsListMarkersAndInlineSyntax() {
        // PDFs write real lists as text, so these stay (Q-API-02, option A)
        assertThat(Markdown.escape("- item")).isEqualTo("- item");
        assertThat(Markdown.escape("* item")).isEqualTo("* item");
        assertThat(Markdown.escape("1. item")).isEqualTo("1. item");
        assertThat(Markdown.escape("--- text after the dashes")).isEqualTo("--- text after the dashes");
        assertThat(Markdown.escape("Price *special* offer, a > b")).isEqualTo("Price *special* offer, a > b");
    }

    @Test
    void keepsHashInsideLine() {
        assertThat(Markdown.escape("C# and F#")).isEqualTo("C# and F#");
        assertThat(Markdown.escape("issue #42")).isEqualTo("issue #42");
    }

    @Test
    void linkKeepsSpacesOutsideAndEscapesBrackets() {
        assertThat(Markdown.link(" the guide ", "https://example.org/")).isEqualTo(" [the guide](https://example.org/) ");
        assertThat(Markdown.link("Java [language]", "https://en.wikipedia.org/wiki/Java_(language)"))
                .isEqualTo("[Java \\[language\\]](<https://en.wikipedia.org/wiki/Java_(language)>)");
        assertThat(Markdown.link("Form", "https://example.org/a%20")).isEqualTo("[Form](https://example.org/a)");
    }

    @Test
    void linkStaysTextWhenItAddsNothingOrIsUnsafe() {
        assertThat(Markdown.link("  ", "https://example.org/")).isEqualTo("  ");
        assertThat(Markdown.link("here", "javascript:alert(1)")).isEqualTo("here");
        assertThat(Markdown.link("here", "file:///etc/passwd")).isEqualTo("here");
        assertThat(Markdown.link("here", null)).isEqualTo("here");
    }

    @Test
    void linkToItselfIsAnAutolink() {
        // "1http://..." after a footnote number is no GFM autolink; <...> works anywhere on the line
        assertThat(Markdown.link("https://example.org/", "https://example.org/%20")).isEqualTo("<https://example.org/>");
        assertThat(Markdown.link(" https://example.org/a ", "https://example.org/a")).isEqualTo(" <https://example.org/a> ");
        assertThat(Markdown.link("team@example.org", "mailto:team@example.org")).isEqualTo("<team@example.org>");
        assertThat(Markdown.link("mailto:team@example.org", "mailto:team@example.org"))
                .isEqualTo("<mailto:team@example.org>");
    }

    @Test
    void linkToItselfThatNoAutolinkCanHoldStaysText() {
        assertThat(Markdown.link("javascript:alert(1)", "javascript:alert(1)")).isEqualTo("javascript:alert(1)");
        assertThat(Markdown.link("https://example.org/a<b", "https://example.org/a<b")).isEqualTo("https://example.org/a<b");
        assertThat(Markdown.link("https://example.org/a b", "https://example.org/a b")).isEqualTo("https://example.org/a b");
        assertThat(Markdown.link("not an@address!", "mailto:not an@address!")).isEqualTo("not an@address!");
        // a piece of an address broken over lines is no address of its own
        assertThat(Markdown.link("example.org/guide", "https://example.org/guide")).isEqualTo("example.org/guide");
    }

    @Test
    void controlCharactersInAHostileAddressCannotBreakTheDocument() {
        // a PDF link annotation is any string: "\n\n# heading" in it added a heading to the Markdown
        assertThat(Markdown.link("text", "https://example.com/a\n\n# injected heading"))
                .isEqualTo("[text](<https://example.com/a%0A%0A# injected heading>)");
        assertThat(Markdown.link("text", "https://example.com/a\rb\u0000c\td"))
                .isEqualTo("[text](https://example.com/a%0Db%00c%09d)");
        assertThat(Markdown.link("two\nlines", "https://example.com/")).isEqualTo("[two lines](https://example.com/)");
    }

    @Test
    void pieceOfAnAddressSplitOverLinesStaysText() {
        // RFC 9562 and Chrome print long addresses over two lines, each line with its own link box
        assertThat(Markdown.link("https://", "https://github.com/chilts/sid")).isEqualTo("https://");
        assertThat(Markdown.link("github.com/chilts/sid", "https://github.com/chilts/sid"))
                .isEqualTo("github.com/chilts/sid");
        assertThat(Markdown.link("https://doi.org/10.6028/X.", "https://doi.org/10.6028/X"))
                .isEqualTo("https://doi.org/10.6028/X.");
        assertThat(Markdown.link("NIST.FIPS.202.pdf", "https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.202.pdf"))
                .isEqualTo("NIST.FIPS.202.pdf");
        assertThat(Markdown.link("ction-6.8)", "https://www.rfc-editor.org/rfc/rfc9562.html#section-6.8"))
                .isEqualTo("ction-6.8)");
        // a name that happens to be in the address is still a link
        assertThat(Markdown.link("Btrfs", "https://en.wikipedia.org/wiki/Btrfs"))
                .isEqualTo("[Btrfs](https://en.wikipedia.org/wiki/Btrfs)");
        assertThat(Markdown.link("128-bit", "https://en.wikipedia.org/wiki/128-bit"))
                .isEqualTo("[128-bit](https://en.wikipedia.org/wiki/128-bit)");
        assertThat(Markdown.link("www.gov.uk/gca", "http://www.gca.gov.uk/"))
                .isEqualTo("[www.gov.uk/gca](http://www.gca.gov.uk/)");
    }

    @Test
    void unifiesLineEndings() {
        assertThat(Markdown.normalize("a\r\nb\rc")).isEqualTo("a\nb\nc\n");
    }

    @Test
    void removesTrailingSpaces() {
        assertThat(Markdown.normalize("a  \nb\t")).isEqualTo("a\nb\n");
    }

    @Test
    void collapsesBlankLines() {
        assertThat(Markdown.normalize("a\n\n\n\nb")).isEqualTo("a\n\nb\n");
    }

    @Test
    void treatsWhitespaceOnlyLinesAsBlank() {
        assertThat(Markdown.normalize("a\n   \nb")).isEqualTo("a\n\nb\n");
    }

    @Test
    void dropsLeadingAndTrailingBlankLines() {
        assertThat(Markdown.normalize("\n\n a\n\n")).isEqualTo(" a\n");
    }

    @Test
    void returnsEmptyStringForBlankText() {
        assertThat(Markdown.normalize(" \n\n \n")).isEmpty();
    }

    @Test
    void edgeCasesOfLineEndings() {
        assertThat(Markdown.normalize("")).isEmpty();
        assertThat(Markdown.normalize("\n")).isEmpty();
        assertThat(Markdown.normalize("\r")).isEmpty();
        assertThat(Markdown.normalize("\r\n")).isEmpty();
        assertThat(Markdown.normalize("a")).isEqualTo("a\n");
        assertThat(Markdown.normalize("a\n")).isEqualTo("a\n");
        assertThat(Markdown.normalize("a\n\n")).isEqualTo("a\n");
        assertThat(Markdown.normalize("a\r\n")).isEqualTo("a\n");
        assertThat(Markdown.normalize("a\r")).isEqualTo("a\n");
        assertThat(Markdown.normalize("a \t\n\n\n")).isEqualTo("a\n");
        assertThat(Markdown.normalize("a\r\n\r\n\r\nb")).isEqualTo("a\n\nb\n");
        assertThat(Markdown.normalize("a\r\r\rb")).isEqualTo("a\n\nb\n");
        // "\n\r" is two line ends, "\r\n" is one
        assertThat(Markdown.normalize("a\n\rb")).isEqualTo("a\n\nb\n");
        assertThat(Markdown.normalize("a\r\nb")).isEqualTo("a\nb\n");
    }
}
