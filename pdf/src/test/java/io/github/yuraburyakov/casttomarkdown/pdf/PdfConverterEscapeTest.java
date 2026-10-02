package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PdfConverterEscapeTest {

    @Test
    void escapesHashAtLineStart() {
        // arXiv table text and RFC code comments start with "#".
        assertThat(PdfConverter.escape("# layers 1+2n")).isEqualTo("\\# layers 1+2n");
        assertThat(PdfConverter.escape("## two")).isEqualTo("\\## two");
        assertThat(PdfConverter.escape("  # indented")).isEqualTo("  \\# indented");
    }

    @Test
    void keepsHashInsideLine() {
        assertThat(PdfConverter.escape("C# and F#")).isEqualTo("C# and F#");
        assertThat(PdfConverter.escape("issue #42")).isEqualTo("issue #42");
    }

    @Test
    void bodyLineStartingWithHashDoesNotBecomeHeading() {
        List<Line> lines = List.of(
                new Line(1, 72, 100, 9, "# layers 1+2n 2n 2n"),
                new Line(1, 72, 111, 9, "# filters 16 32 64"));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("\\# layers 1+2n 2n 2n\n\\# filters 16 32 64");
    }
}
