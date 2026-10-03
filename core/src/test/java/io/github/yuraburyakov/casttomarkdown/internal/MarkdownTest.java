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
    void keepsHashInsideLine() {
        assertThat(Markdown.escape("C# and F#")).isEqualTo("C# and F#");
        assertThat(Markdown.escape("issue #42")).isEqualTo("issue #42");
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
