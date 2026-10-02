package io.github.yuraburyakov.casttomarkdown.internal.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Positions in these tests are taken from real PDFs (Obsidian: "CastToMarkdown - Experiment 01"). */
class PageFurnitureTest {

    private static final float A4 = 842;

    @Test
    void removesRunningHeaderAndFooterWithPageNumber() {
        // RFC 9562: header at 5% of the page height, footer with the page number at 87%.
        List<Line> lines = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            lines.add(line(page, 56.3f, 400, "Body text of page " + page));
            lines.add(line(page, 56.3f, 44.6f, "RFC 9562 UUIDs May 2024"));
            lines.add(line(page, 56.3f, 732.9f, "Davis, et al. Standards Track Page " + page));
        }

        assertThat(texts(PageFurniture.remove(lines))).containsExactly(
                "Body text of page 1", "Body text of page 2", "Body text of page 3",
                "Body text of page 4", "Body text of page 5");
    }

    @Test
    void romanAndArabicPageNumbersMatch() {
        // NIST: front matter numbered ii, iii, body numbered 1, 2, 3, at the same place.
        List<Line> lines = new ArrayList<>();
        String[] numbers = {"ii", "iii", "1", "2", "3"};
        for (int page = 1; page <= numbers.length; page++) {
            lines.add(line(page, 72, 400, "Body " + page));
            lines.add(line(page, 300, 780, numbers[page - 1]));
        }

        assertThat(texts(PageFurniture.remove(lines))).containsExactly("Body 1", "Body 2", "Body 3", "Body 4", "Body 5");
    }

    @Test
    void keepsRepeatedTextInTheMiddleOfThePage() {
        // RFC 9562: requirement keywords repeat on many pages, but inside the text.
        List<Line> lines = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            lines.add(line(page, 150, 330, "SHOULD"));
        }

        assertThat(PageFurniture.remove(lines)).hasSize(5);
    }

    @Test
    void keepsNumbersAtPageEdgeThatChangePlace() {
        // arXiv: chart labels near the top edge are numbers too, but not in the place of the page number.
        List<Line> lines = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            lines.add(line(page, 295, 741, String.valueOf(page)));
        }
        lines.add(line(5, 106, 76, "20"));
        lines.add(line(5, 424, 76, "20"));

        assertThat(texts(PageFurniture.remove(lines))).containsExactly("20", "20");
    }

    @Test
    void removesRepeatedRotatedMarginTextButNotRotatedNumbers() {
        // NIST: vertical text in the side margin of every page; arXiv: rotated chart axis numbers.
        List<Line> lines = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            lines.add(rotated(page, 540, 589, "This publication is available free of charge from"));
            lines.add(rotated(page, 488, 315, "60"));
        }

        assertThat(texts(PageFurniture.remove(lines))).containsOnly("60").hasSize(5);
    }

    @Test
    void keepsEverythingInShortDocuments() {
        List<Line> lines = List.of(
                line(1, 56, 44, "Title repeated on top"),
                line(2, 56, 44, "Title repeated on top"));

        assertThat(PageFurniture.remove(lines)).hasSize(2);
    }

    private static Line line(int page, float x, float y, String text) {
        return new Line(page, A4, x, y, 12, false, false, text);
    }

    private static Line rotated(int page, float x, float y, String text) {
        return new Line(page, A4, x, y, 9, false, true, text);
    }

    private static List<String> texts(List<Line> lines) {
        return lines.stream().map(Line::text).toList();
    }
}
