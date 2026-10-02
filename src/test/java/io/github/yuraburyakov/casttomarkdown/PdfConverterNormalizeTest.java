package io.github.yuraburyakov.casttomarkdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PdfConverterNormalizeTest {

    @Test
    void unifiesLineEndings() {
        assertThat(PdfConverter.normalize("a\r\nb\rc")).isEqualTo("a\nb\nc\n");
    }

    @Test
    void removesTrailingSpaces() {
        assertThat(PdfConverter.normalize("a  \nb\t")).isEqualTo("a\nb\n");
    }

    @Test
    void collapsesBlankLines() {
        assertThat(PdfConverter.normalize("a\n\n\n\nb")).isEqualTo("a\n\nb\n");
    }

    @Test
    void treatsWhitespaceOnlyLinesAsBlank() {
        assertThat(PdfConverter.normalize("a\n   \nb")).isEqualTo("a\n\nb\n");
    }

    @Test
    void dropsLeadingAndTrailingBlankLines() {
        assertThat(PdfConverter.normalize("\n\n a\n\n")).isEqualTo(" a\n");
    }

    @Test
    void returnsEmptyStringForBlankText() {
        assertThat(PdfConverter.normalize(" \n\n \n")).isEmpty();
    }
}
