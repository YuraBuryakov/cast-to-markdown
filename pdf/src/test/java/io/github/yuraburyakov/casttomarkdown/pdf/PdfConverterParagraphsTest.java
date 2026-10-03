package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Numbers in these tests are taken from real PDFs (Obsidian: "CastToMarkdown - Experiment 01"). */
class PdfConverterParagraphsTest {

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
