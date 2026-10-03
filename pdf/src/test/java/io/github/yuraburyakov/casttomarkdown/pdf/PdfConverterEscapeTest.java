package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PdfConverterEscapeTest {

    @Test
    void bodyLineStartingWithHashDoesNotBecomeHeading() {
        List<Line> lines = List.of(
                new Line(1, 72, 100, 9, "# layers 1+2n 2n 2n"),
                new Line(1, 72, 111, 9, "# filters 16 32 64"));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("\\# layers 1+2n 2n 2n\n\\# filters 16 32 64");
    }

    @Test
    void codeFenceQuoteAndRuleInTextStayText() {
        List<Line> lines = List.of(
                new Line(1, 72, 100, 9, "```bash is how the example starts"),
                new Line(1, 72, 111, 9, "> quoted from the original"),
                new Line(1, 72, 122, 9, "---"),
                new Line(1, 72, 133, 9, "- a dash item stays a list item"));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(
                "\\```bash is how the example starts\n\\> quoted from the original\n\\---\n- a dash item stays a list item");
    }
}
