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
}
