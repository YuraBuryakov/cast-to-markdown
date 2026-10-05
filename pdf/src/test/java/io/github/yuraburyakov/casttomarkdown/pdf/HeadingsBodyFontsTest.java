package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingsBodyFontsTest {
    private static List<Line> mixed(int secondaryCount, float secondarySize, boolean bold) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(1, 50, 60, 17, true, "Document title"));
        lines.add(new Line(1, 50, 100, 11, true, "1 Introduction"));
        float y = 120;
        for (int i = 0; i < 24; i++) {
            lines.add(new Line(1, 50, y, 9,
                    "The ordinary body text continues with sufficient characters to establish the primary font and its weight."));
            y += 12;
            if (i < secondaryCount) {
                lines.add(new Line(1, 50, y, secondarySize, bold,
                        "training for 3.5 days on eight GPUs, a small fraction of the training costs of the"));
                y += 12;
            }
        }
        return lines;
    }

    @Test
    void frequentSecondBodyFontDoesNotCreateHeadingsAndRealHeadingsSurvive() {
        String markdown = PdfConverter.toMarkdown(mixed(18, 10, false));
        assertThat(markdown).contains("# Document title", "## 1 Introduction");
        assertThat(markdown.lines().filter(s -> s.startsWith("#")).toList())
                .containsExactly("# Document title", "## 1 Introduction");
        assertThat(markdown.split("training for 3.5 days", -1)).hasSize(19);
    }

    @Test
    void numberedSubsectionInSecondarySizeRemainsHeading() {
        List<Line> lines = mixed(18, 10, false);
        lines.add(2, new Line(1, 50, 110, 10, false, "1.1 Encoder and Decoder Stacks"));
        assertThat(PdfConverter.toMarkdown(lines)).contains("### 1.1 Encoder and Decoder Stacks");
    }

    @Test
    void rareCloseSizeIsStillEligibleForHeading() {
        assertThat(PdfConverter.toMarkdown(mixed(1, 10, false)).lines()
                .filter(s -> s.startsWith("#") && s.contains("training for"))) .isNotEmpty();
    }

    @Test
    void repeatedBoldTextDoesNotEstablishSecondBodyFont() {
        assertThat(PdfConverter.toMarkdown(mixed(18, 10, true)).lines()
                .filter(s -> s.startsWith("#") && s.contains("training for"))).isNotEmpty();
    }

    @Test
    void frequentFontsInSeparateSectionsDoNotCountAsMixedBody() {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            lines.add(new Line(1, 50, 120 + i * 12, 9,
                    "Primary body font with substantial content and sufficient characters to dominate the whole document."));
        }
        for (int i = 0; i < 18; i++) {
            lines.add(new Line(2, 50, 120 + i * 12, 10,
                    "Secondary section heading or text with a larger font and no repeated within-paragraph alternation."));
        }
        List<List<Line>> paragraphs = lines.stream().map(List::of).toList();
        assertThat(java.util.Arrays.stream(Headings.levels(paragraphs)).filter(level -> level > 0).count()).isGreaterThan(0);
    }

    @Test
    void moreDistantFontSizeIsNotAbsorbedIntoBody() {
        assertThat(PdfConverter.toMarkdown(mixed(18, 11, false)).lines()
                .filter(s -> s.startsWith("#") && s.contains("training for"))).isNotEmpty();
    }
}
