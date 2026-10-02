package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Font sizes in these tests are taken from real PDFs (Obsidian: "CastToMarkdown - Experiment 01"). */
class PdfConverterHeadingsTest {

    private static final String BODY = "Body text in the most common font size of the document.";

    @Test
    void mapsHeadingFontSizesToLevelsLargestFirst() {
        // RFC 9562: title 22 pt, sections 19 pt, subsections 16 pt, body 13 pt.
        List<Line> lines = List.of(
                line(80, 22, "Universally Unique IDentifiers"),
                line(120, 13, BODY),
                line(160, 19, "Abstract"),
                line(200, 13, BODY),
                line(240, 16, "2.1. Update Motivation"),
                line(280, 13, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "# Universally Unique IDentifiers", BODY,
                "## Abstract", BODY,
                "### 2.1. Update Motivation", BODY));
    }

    @Test
    void joinsTwoLineHeadingWithSpace() {
        List<Line> lines = List.of(
                line(80, 22, "RFC 9562"),
                line(105, 22, "Universally Unique IDentifiers (UUIDs)"),
                line(150, 13, BODY));

        assertThat(PdfConverter.toMarkdown(lines))
                .isEqualTo("# RFC 9562 Universally Unique IDentifiers (UUIDs)\n\n" + BODY);
    }

    @Test
    void headingMayBeFollowedByAnotherHeading() {
        // arXiv: title 14 pt, then "1. Introduction" 11 pt, then body 9 pt.
        List<Line> lines = List.of(
                line(80, 14, "Deep Residual Learning for Image Recognition"),
                line(120, 11, "1. Introduction"),
                line(140, 9, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "# Deep Residual Learning for Image Recognition", "## 1. Introduction", BODY));
    }

    @Test
    void largeTextFollowedBySmallFigureTextIsNotHeading() {
        // arXiv: a 12 pt axis label inside a plot, followed by 7 pt figure text.
        List<Line> lines = List.of(
                line(80, 9, BODY),
                line(120, 12, "layer index (sorted by magnitude)"),
                line(140, 7, "Figure 7. Standard deviations of layer responses."));

        assertThat(PdfConverter.toMarkdown(lines)).doesNotContain("#");
    }

    @Test
    void textFragmentsWithoutWordsAreNotHeadings() {
        // arXiv: the rotated 20 pt "arXiv:1512.03385v1" watermark comes out as fragments.
        List<Line> lines = List.of(
                line(80, 20, "ar"),
                line(120, 20, "X"),
                line(160, 9, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).doesNotContain("#");
    }

    @Test
    void titleInMuchLargerFontMayTakeUpToFourLines() {
        // Word 365: a 26 pt title over three lines, body 12 pt.
        List<Line> lines = List.of(
                bold(80, 26, "Guidance for licensing authorities to"),
                bold(110, 26, "prevent illegal working in the taxi and"),
                bold(140, 26, "private hire sector in the UK"),
                line(190, 12, BODY),
                line(204, 12, BODY),
                line(218, 12, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(
                "# Guidance for licensing authorities to prevent illegal working in the taxi and private hire sector in the UK"
                        + "\n\n" + BODY + "\n" + BODY + "\n" + BODY);
    }

    @Test
    void longParagraphInLargeFontIsNotHeading() {
        List<Line> lines = List.of(
                line(80, 13, "Paul A. Grassi"),
                line(96, 13, "Michael E. Garcia"),
                line(112, 13, "James L. Fenton"),
                line(150, 12, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).doesNotContain("#");
    }

    @Test
    void boldNumberedBodySizeTextIsHeadingWithLevelFromNumber() {
        // NIST SP 800-63-3 (Word): body 12 pt; sections bold 12 pt, subsections bold 11 pt.
        List<Line> lines = List.of(
                bold(80, 12, "2 Introduction"),
                line(110, 12, BODY),
                bold(140, 11, "2.5 Change History"),
                bold(170, 11, "2.5.1 SP 800-63-1"),
                line(200, 12, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "## 2 Introduction", BODY, "### 2.5 Change History", "#### 2.5.1 SP 800-63-1", BODY));
    }

    @Test
    void boldBodySizeTextWithoutNumberIsNotHeading() {
        // NIST glossary: bold 12 pt terms followed by their definitions.
        List<Line> lines = List.of(
                bold(80, 12, "Active Attack"),
                line(110, 12, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).doesNotContain("#");
    }

    @Test
    void tableOfContentsEntryIsNotHeading() {
        List<Line> lines = List.of(
                bold(80, 12, "1 Purpose ........................................ 1"),
                line(110, 12, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).doesNotContain("#");
    }

    @Test
    void unnumberedHeadingTakesLevelOfNumberedHeadingsInSameFont() {
        // RFC 9562: "Abstract" and "1. Introduction" are both bold 19 pt, the title is bold 22 pt.
        List<Line> lines = List.of(
                bold(80, 22, "Universally Unique IDentifiers"),
                line(120, 13, BODY),
                bold(160, 19, "Abstract"),
                line(200, 13, BODY),
                bold(240, 19, "1. Introduction"),
                line(280, 13, BODY),
                bold(320, 16, "2.1. Update Motivation"),
                line(360, 13, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "# Universally Unique IDentifiers", BODY,
                "## Abstract", BODY,
                "## 1. Introduction", BODY,
                "### 2.1. Update Motivation", BODY));
    }

    @Test
    void boldParagraphSignHeadingIsSplitFromFollowingTextWithoutGap() {
        // LibreOffice statutes: bold 10 pt "§ 1 ..." directly followed by regular 10 pt text, same line pitch.
        List<Line> lines = List.of(
                line(80, 10, BODY),
                bold(110, 10, "§ 1 Name, Legal Form, Headquarters and Financial Year"),
                line(122, 10, "(1) The name of the foundation is \"The Document Foundation\"."),
                line(134, 10, "(2) It has its headquarters in Berlin."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                BODY,
                "## § 1 Name, Legal Form, Headquarters and Financial Year",
                "(1) The name of the foundation is \"The Document Foundation\".\n(2) It has its headquarters in Berlin."));
    }

    @Test
    void mostlyBoldFirstLineOfDefinitionStaysInItsParagraph() {
        // Word glossary: the term makes the first line mostly bold; the definition continues in regular text.
        List<Line> lines = List.of(
                bold(80, 12, "'UK digital verification services trust Framework' (DVS Trust Framework) is a set of"),
                line(94, 12, "rules and standards for digital identity services."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(
                "'UK digital verification services trust Framework' (DVS Trust Framework) is a set of\n"
                        + "rules and standards for digital identity services.");
    }

    private static Line line(double y, double fontSize, String text) {
        return new Line(1, 72, (float) y, (float) fontSize, text);
    }

    private static Line bold(double y, double fontSize, String text) {
        return new Line(1, 72, (float) y, (float) fontSize, true, text);
    }
}
