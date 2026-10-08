package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Numbers in these tests are taken from real PDFs (Obsidian: "CastToMarkdown - Experiment 01"). */
class PdfConverterParagraphsTest {

    @Test
    void sentenceGoesOnFromTheRightColumnToTheLeftOneOfTheNextPage() {
        // arXiv 1608.06993 pages 7-8: "direct connections be-" at x 308.9, "tween the surrounding" at x 50.1
        List<Line> lines = List.of(
                line(1, 308.9, 711, 10, "are randomly dropped, which creates direct connections be-"),
                line(2, 50.1, 82, 10, "tween the surrounding layers. As the pooling layers are"));

        assertThat(PdfConverter.toMarkdown(lines)).doesNotContain("\n\n");
    }

    @Test
    void listItemGoesOnAtItsHangingIndentOnTheNextPage() {
        // tdf-statutes.pdf: the item starts at 57.3 pt on page 1, its next line is at 68 pt on page 2
        List<Line> lines = List.of(
                line(1, 57.3, 776.7, 10, "- Intellectual and professional support for the persons who distribute the software or relevant"),
                line(2, 68, 65.6, 10, "documentation, or contribute in any other way thereto;"));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(
                "- Intellectual and professional support for the persons who distribute the software or relevant\n"
                        + "documentation, or contribute in any other way thereto;");
    }

    @Test
    void sentenceGoesOnInTheNextColumn() {
        // arXiv 1512.00567: the left column ends "yielded simi-", the right one starts "larly high performance"
        List<Line> lines = List.of(
                line(1, 50, 600, 10, "networks. VGGNet [18] and GoogLeNet [20] yielded simi-"),
                line(1, 308, 72, 10, "larly high performance in the 2014 ILSVRC classification."),
                line(1, 308, 100, 10, "A new paragraph."));

        // one paragraph; "similarly" is an English word and "larly" is none, so the hyphen goes (see Hyphens)
        assertThat(PdfConverter.toMarkdown(lines)).startsWith(
                "networks. VGGNet [18] and GoogLeNet [20] yielded similarly\nhigh performance in the 2014 ILSVRC classification.\n\n");
    }

    @Test
    void capitalAtTheTopOfTheNextColumnStartsAParagraph() {
        List<Line> lines = List.of(
                line(1, 50, 600, 10, "The left column ends with a line that has no full stop"),
                line(1, 308, 72, 10, "The right column starts a new paragraph."));

        assertThat(PdfConverter.toMarkdown(lines)).contains("full stop\n\nThe right column");
    }

    @Test
    void itemWithAHangingIndentIsOneParagraph() {
        // arXiv 1404.7828: roman numbered items, the second line indented, the next item back at the margin
        List<Line> lines = List.of(
                line(1, 118.2, 114.8, 9, "II LSTM-like networks alleviate the problem through a special"),
                line(1, 129.8, 126.8, 9, "architecture unaffected by it."),
                line(1, 114.9, 138.8, 9, "III Today's GPU-based computers have a million times the power."));

        assertThat(PdfConverter.toMarkdown(lines)).startsWith(
                "II LSTM-like networks alleviate the problem through a special\narchitecture unaffected by it.");
    }

    @Test
    void sentenceGoesOnPastTheFootnotesOfItsPage() {
        // arXiv 1706.03762 pages 4-5: "yielding dv-dimensional" / four footnote lines in 8 pt / "output values"
        List<Line> lines = List.of(
                line(1, 108, 700, 10, "We then perform the attention function in parallel, yielding dv-dimensional"),
                line(1, 108, 720, 8, "4To illustrate why the dot products get large, assume that the components"),
                line(1, 108, 730, 8, "of q and k are independent random variables."),
                line(2, 108, 72, 10, "output values. These are concatenated and once again projected."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "We then perform the attention function in parallel, yielding dv-dimensional\n"
                        + "output values. These are concatenated and once again projected.",
                "4To illustrate why the dot products get large, assume that the components\n"
                        + "of q and k are independent random variables."));
    }

    @Test
    void newParagraphOnTheNextPageStaysApartFromTheFootnotes() {
        // a capital letter on the next page starts a paragraph of its own: the footnotes stay where they are
        List<Line> lines = List.of(
                line(1, 108, 700, 10, "The last line of this page ends without any full stop at all"),
                line(1, 108, 720, 8, "1A footnote."),
                line(2, 108, 72, 10, "Another paragraph starts on the next page."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "The last line of this page ends without any full stop at all", "1A footnote.",
                "Another paragraph starts on the next page."));
    }

    @Test
    void separatesParagraphsByGapRelativeToTypicalLinePitch() {
        // RFC 9562 (WeasyPrint): 13 pt font, line pitch 13.6, paragraph gap 23.6.
        // PDFBox reports a glyph height of 4.28 here and puts every line into its own paragraph.
        List<Line> lines = List.of(
                line(1, 72, 307.5, 13, "This specification defines UUIDs"),
                line(1, 72, 321.1, 13, "(Globally Unique IDentifiers) and"),
                line(1, 72, 334.7, 13, "platforms."),
                line(1, 72, 358.3, 13, "This specification is derived"),
                line(1, 72, 371.9, 13, "from the OSF DCE specification."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                This specification defines UUIDs
                (Globally Unique IDentifiers) and
                platforms.

                This specification is derived
                from the OSF DCE specification.""");
    }

    @Test
    void startsParagraphAtFirstLineIndentWithoutGap() {
        // arXiv (pdfTeX): 9 pt font, pitch 12, paragraphs marked only by a 12 pt first-line indent.
        List<Line> lines = List.of(
                line(1, 50.1, 189.7, 9, "are comparably good or better than the constructed"),
                line(1, 50.1, 201.7, 9, "(or unable to do so in feasible time)."),
                line(1, 62.1, 213.7, 9, "In this paper, we address the degradation problem by"),
                line(1, 50.1, 225.7, 9, "introducing a deep residual learning framework."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                are comparably good or better than the constructed
                (or unable to do so in feasible time).

                In this paper, we address the degradation problem by
                introducing a deep residual learning framework.""");
    }

    @Test
    void keepsHangingIndentOfListItemInOneParagraph() {
        List<Line> lines = List.of(
                line(1, 72, 100, 12, "• FAL refers to the strength of an assertion, used to"),
                line(1, 90, 114, 12, "communicate authentication information."),
                line(1, 72, 128, 12, "• Next item."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                - FAL refers to the strength of an assertion, used to
                communicate authentication information.
                - Next item.""");
    }

    @Test
    void startsParagraphWhenFontSizeChanges() {
        List<Line> lines = List.of(
                line(1, 72, 100, 13, "This specification defines UUIDs."),
                line(1, 72, 114, 9, "1 A footnote."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("This specification defines UUIDs.\n\n1 A footnote.");
    }

    @Test
    void startsParagraphOnNewPageAndWhenTextMovesUp() {
        List<Line> lines = List.of(
                line(1, 50, 700, 9, "end of the left column"),
                line(1, 300, 80, 9, "top of the right column"),
                line(2, 50, 80, 9, "next page"));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                end of the left column

                top of the right column

                next page""");
    }

    @Test
    void sentenceCutByThePageEndStaysOneParagraph() {
        List<Line> lines = List.of(
                line(1, 72, 700, 11, "In analyzing risks, the agency SHALL consider all of the"),
                line(1, 72, 714, 11, "expected direct and indirect results of an authentication"),
                line(2, 72, 90, 11, "failure, including the impact of the failure."),
                line(2, 72, 104, 11, "This continues the same paragraph."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                In analyzing risks, the agency SHALL consider all of the
                expected direct and indirect results of an authentication
                failure, including the impact of the failure.
                This continues the same paragraph.""");
    }

    @Test
    void finishedSentenceOrListItemOnTheNextPageStartsAParagraph() {
        List<Line> lines = List.of(
                line(1, 72, 714, 11, "The page ends with a full sentence."),
                line(2, 72, 90, 11, "A new paragraph starts the page"),
                line(2, 72, 104, 11, "and ends without punctuation"),
                line(3, 72, 90, 11, "- but the next page starts with a list item"),
                line(3, 72, 104, 11, "Right to work checks for licence holders ............ 32"),
                line(4, 72, 90, 11, "EU Settlement Scheme status granted and pending ..... 33"),
                line(4, 72, 104, 11, "Note"),
                line(5, 72, 90, 11, "Step Instruction Screen example"));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                The page ends with a full sentence.

                A new paragraph starts the page
                and ends without punctuation

                - but the next page starts with a list item
                Right to work checks for licence holders ............ 32

                EU Settlement Scheme status granted and pending ..... 33
                Note

                Step Instruction Screen example""");
    }

    @Test
    void returnsEmptyStringForNoLines() {
        assertThat(PdfConverter.toMarkdown(List.of())).isEmpty();
    }

    private static Line line(int page, double x, double y, double fontSize, String text) {
        return new Line(page, (float) x, (float) y, (float) fontSize, text);
    }
}
