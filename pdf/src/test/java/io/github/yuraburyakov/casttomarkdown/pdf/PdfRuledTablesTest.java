package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A captioned table drawn as a grid of rules becomes a Markdown table, empty cells included. */
class PdfRuledTablesTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void captionedRuledTableBecomesMarkdownTable() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .line(720, "Text before.")
                .rule(72, 700, 300)
                .rule(72, 684, 300)
                .rule(72, 652, 300)
                .verticalRule(172, 700, 48)
                .verticalRule(272, 700, 48)
                .line(76, 690, "Model")
                .line(176, 690, "Top-1")
                .line(276, 690, "Top-5")
                .line(76, 672, "ResNet")
                .line(276, 672, "7.8")
                .line(76, 660, "VGG")
                .line(176, 660, "28.1")
                .line(276, 660, "9.3")
                .line(636, "Table 1. Results.")
                .line(600, "Text after.")
                .writeTo(dir.resolve("table.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                Text before.

                | Model | Top-1 | Top-5 |
                | --- | --- | --- |
                | ResNet |  | 7.8 |
                | VGG | 28.1 | 9.3 |

                Table 1. Results.

                Text after.
                """);
    }

    @Test
    void ruledTableWithoutCaptionStaysText() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .rule(72, 700, 300)
                .rule(72, 652, 300)
                .verticalRule(172, 700, 48)
                .line(76, 672, "ResNet")
                .line(176, 672, "7.8")
                .writeTo(dir.resolve("no-caption.pdf"));

        assertThat(converter.convert(pdf).markdown()).doesNotContain("|").contains("ResNet").contains("7.8");
    }
}
