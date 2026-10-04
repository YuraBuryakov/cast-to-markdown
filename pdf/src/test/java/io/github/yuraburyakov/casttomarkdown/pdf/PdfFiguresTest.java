package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Text inside a captioned figure is left out; the caption stays. */
class PdfFiguresTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void textInsideACaptionedFigureIsLeftOut() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .line(720, "Text before the figure.")
                .rect(100, 560, 300, 120)
                .line(150, 640, "3x3 conv, 64")
                .line(150, 600, "relu")
                .line(540, "Figure 1. A residual block.")
                .line(500, "Text after the figure.")
                .writeTo(dir.resolve("figure.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("Text before the figure.\n\nFigure 1. A residual block.\n\nText after the figure.\n");
    }

    @Test
    void textBetweenTableRulesStays() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .rule(100, 680, 300)
                .line(150, 620, "model err")
                .rule(100, 560, 300)
                .line(540, "Figure 2. Only rules.")
                .writeTo(dir.resolve("rules.pdf"));

        assertThat(converter.convert(pdf).markdown()).contains("model err").contains("Figure 2. Only rules.");
    }
}
