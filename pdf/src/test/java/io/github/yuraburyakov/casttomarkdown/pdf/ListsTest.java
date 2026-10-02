package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Positions in these tests are taken from real PDFs (Obsidian: "CastToMarkdown - Experiment 01"). */
class ListsTest {

    @Test
    void rendersBulletAsMarkdownItem() {
        assertThat(Lists.markdown("• IAL refers to the identity proofing process."))
                .isEqualTo("- IAL refers to the identity proofing process.");
        // InDesign: a tab after the marker.
        assertThat(Lists.markdown("•\t Email: someone@example.org")).isEqualTo("- Email: someone@example.org");
        assertThat(Lists.markdown("▪ Small square item")).isEqualTo("- Small square item");
    }

    @Test
    void leavesNumberedItemsAndOtherTextAsTheyAre() {
        assertThat(Lists.markdown("1. First step")).isEqualTo("1. First step");
        assertThat(Lists.markdown("Text with • inside")).isEqualTo("Text with • inside");
    }

    @Test
    void movesSeparateMarkerToItsLine() {
        // RFC 9562 (WeasyPrint): markers come after the item text, at the same height, 6 pt to the left.
        List<Line> lines = List.of(
                new Line(20, 85.9f, 104.8f, 13, "An implementation would like to embed extra information"),
                new Line(20, 85.9f, 118.4f, 13, "is defined in this document."),
                new Line(20, 79.7f, 104.8f, 13, "•"));

        assertThat(Lists.attachMarkers(lines)).extracting(Line::text).containsExactly(
                "• An implementation would like to embed extra information",
                "is defined in this document.");
    }

    @Test
    void keepsMarkerWithoutTextNextToIt() {
        List<Line> lines = List.of(
                new Line(1, 72, 100, 12, "Some text"),
                new Line(1, 60, 300, 12, "•"));

        assertThat(Lists.attachMarkers(lines)).isEqualTo(lines);
    }

    @Test
    void separateMarkersBecomeMarkdownList() {
        List<Line> lines = List.of(
                new Line(20, 72, 80, 13, "Some example situations:"),
                new Line(20, 85.9f, 104.8f, 13, "An implementation would like to embed extra information"),
                new Line(20, 85.9f, 118.4f, 13, "is defined in this document."),
                new Line(20, 85.9f, 134.5f, 13, "An implementation has other restrictions."),
                new Line(20, 79.7f, 104.8f, 13, "•"),
                new Line(20, 79.7f, 134.5f, 13, "•"));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                Some example situations:

                - An implementation would like to embed extra information
                is defined in this document.
                - An implementation has other restrictions.""");
    }
}
