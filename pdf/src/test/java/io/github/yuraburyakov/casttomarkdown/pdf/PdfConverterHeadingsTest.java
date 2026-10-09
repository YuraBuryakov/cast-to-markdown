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
    void boldNumberedListItemOutOfSectionSequenceIsNotHeading() {
        // RFC 9562 section 5.7: a bold numbered list "1. OPTIONAL", "2. OPTIONAL" inside the section
        List<Line> lines = List.of(
                bold(80, 13, "5.7. UUID Version 7"),
                line(120, 13, BODY),
                bold(160, 13, "1. OPTIONAL"),
                line(180, 13, BODY),
                bold(220, 13, "2. OPTIONAL"),
                line(240, 13, BODY),
                bold(280, 13, "5.8. UUID Version 8"),
                line(320, 13, BODY),
                bold(360, 13, "6. UUID Best Practices"),
                line(400, 13, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "### 5.7. UUID Version 7", BODY,
                "1. OPTIONAL", BODY,
                "2. OPTIONAL", BODY,
                "### 5.8. UUID Version 8", BODY,
                "## 6. UUID Best Practices", BODY));
    }

    @Test
    void headingIsAtMostOneLevelDeeperThanTheOneBefore() {
        // InDesign: the 16 pt font only appears in one section, so 14 pt ranks third everywhere
        List<Line> lines = List.of(
                line(80, 20, "Application window"),
                line(120, 11, BODY),
                line(160, 14, "Changing your application"),
                line(200, 11, BODY),
                line(240, 20, "Help to complete your application"),
                line(280, 16, "Charity signposting"),
                line(320, 11, BODY),
                line(360, 14, "Veterans Welfare Service"),
                line(400, 11, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "# Application window", BODY,
                "## Changing your application", BODY,
                "# Help to complete your application",
                "## Charity signposting", BODY,
                "### Veterans Welfare Service", BODY));
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

    @Test
    void abstractInTheSmallFontOfTheAbstractIsAHeading() {
        // arXiv 1404.7828: "Abstract" in the 8 pt of the abstract under it, body 9 pt
        List<Line> lines = List.of(
                line(100, 14, "Deep Learning in Neural Networks: An Overview"),
                line(336, 8, "Abstract"),
                line(352, 8, "In recent years, deep artificial neural networks have won numerous contests."),
                line(400, 9, BODY),
                line(412, 9, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).contains("\n\n## Abstract\n\n");
    }

    @Test
    void boldAbstractLaterInTheFrontMatterIsAHeading() {
        // NIST SP 800-63-3, page 6: "Abstract" bold in 12 pt, body 12 pt
        List<Line> lines = List.of(
                new Line(6, 72, 94, 12, true, "Abstract"),
                new Line(6, 72, 120, 12, BODY),
                new Line(6, 72, 134, 12, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).startsWith("# Abstract\n\n");
    }

    @Test
    void centredTitleIsOneHeadingBeforeTheAuthors() {
        // arXiv 1810.04805: a 14 pt title in two centred lines, authors in 11 pt, body 10 pt
        List<Line> lines = List.of(
                wide(116.5, 81.7, 14, 365, "BERT: Pre-training of Deep Bidirectional Transformers for"),
                wide(220.9, 97.6, 14, 156, "Language Understanding"),
                wide(121.8, 140.0, 11, 357, "Jacob Devlin Ming-Wei Chang Kenton Lee Kristina Toutanova"),
                wide(107.8, 167.9, 11, 385, "{jacobdevlin,mingweichang}@google.com"),
                line(240, 10, BODY),
                line(252, 10, BODY));

        assertThat(PdfConverter.toMarkdown(lines))
                .startsWith("# BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding\n\n");
    }

    @Test
    void centredHeadingMayTakeThreeShortLines() {
        // arXiv 1810.04805: the 11 pt appendix title in three centred lines, body 10 pt
        List<Line> lines = List.of(
                line(100, 10, BODY),
                wide(85.9, 678.4, 11, 190, "Appendix for “BERT: Pre-training of"),
                wide(88.2, 691.9, 11, 186, "Deep Bidirectional Transformers for"),
                wide(98.0, 705.5, 11, 166, "Language Understanding”"),
                line(730, 10, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).contains(
                "\n\n# Appendix for “BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding”\n\n");
    }

    @Test
    void centredNoticeOfLongLinesIsNoHeading() {
        // arXiv 1706.03762: a centred permission note in 10 pt above the title, body 9 pt
        List<Line> lines = List.of(
                wide(110, 60, 10, 390, "Provided proper attribution is provided, Google hereby grants permission to"),
                wide(112, 72, 10, 386, "reproduce the tables and figures in this paper solely for use in journalistic or"),
                wide(250, 84, 10, 110, "scholarly works."),
                line(140, 9, BODY),
                line(152, 9, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).doesNotContain("#");
    }

    @Test
    void titleInTheFontOfNumberedSectionsIsLevelOne() {
        // word365-taxi.pdf: the title and "1. Introduction" share one font; numbered top sections are level 2
        List<Line> lines = List.of(
                line(100, 16, "Guidance for licensing authorities"),
                line(150, 11, BODY),
                line(200, 16, "1. Introduction"),
                line(250, 11, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "# Guidance for licensing authorities", BODY, "## 1. Introduction", BODY));
    }

    @Test
    void dateUnderTheTitleIsNoHeading() {
        // word365-taxi.pdf (gov.uk): the date in the size of the title
        List<Line> lines = List.of(
                line(100, 16, "Guidance for licensing authorities"),
                line(150, 16, "1 October 2026"),
                line(200, 11, BODY),
                line(214, 11, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).startsWith("# Guidance for licensing authorities\n\n1 October 2026\n\n");
    }

    @Test
    void authorsBeforeTheirAddressesAreNoHeading() {
        // arXiv 1512.03385: authors in 12 pt between the 14 pt title and the e-mail addresses, body 10 pt
        List<Line> lines = List.of(
                line(100, 14, "Deep Residual Learning for Image Recognition"),
                line(130, 12, "Kaiming He Xiangyu Zhang Shaoqing Ren Jian Sun"),
                line(150, 10, "{kahe, v-xiangz, v-shren, jiansun}@microsoft.com"),
                line(190, 12, "Abstract"),
                line(210, 10, BODY),
                line(222, 10, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "# Deep Residual Learning for Image Recognition",
                "Kaiming He Xiangyu Zhang Shaoqing Ren Jian Sun",
                "{kahe, v-xiangz, v-shren, jiansun}@microsoft.com",
                "## Abstract", BODY + "\n" + BODY));
    }

    @Test
    void titleBeforeTheAuthorsIsAHeading() {
        // arXiv 1712.01208: title 17 pt, authors 11 pt over several lines, body 10 pt
        List<Line> lines = List.of(
                line(126.7, 17, "The Case for Learned Index Structures"),
                line(157.0, 11, "Tim Kraska"),
                line(171.0, 11, "MIT"),
                line(184.9, 11, "Cambridge, MA"),
                line(240, 10, BODY),
                line(252, 10, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).startsWith("# The Case for Learned Index Structures\n\n");
    }

    @Test
    void contentsEntryWithPageNumberIsNotHeading() {
        // arXiv 1404.7828: the contents are bold like the headings, page number after the title, no dot leaders
        List<Line> lines = List.of(
                bold(80, 10, "1 Introduction 4"),
                bold(100, 10, "2 Notation 5"),
                line(120, 10, BODY),
                bold(160, 10, "1 Introduction"),
                line(180, 10, BODY),
                bold(220, 10, "2 Notation"),
                line(240, 10, BODY));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo(String.join("\n\n",
                "1 Introduction 4", "2 Notation 5", BODY,
                "## 1 Introduction", BODY,
                "## 2 Notation", BODY));
    }

    private static Line line(double y, double fontSize, String text) {
        return new Line(1, 72, (float) y, (float) fontSize, text);
    }

    private static Line wide(double x, double y, double fontSize, double width, String text) {
        return new Line(1, 792, (float) x, (float) y, (float) fontSize, false, false, text, -1, (float) width,
                (float) x, (float) y);
    }

    private static Line bold(double y, double fontSize, String text) {
        return new Line(1, 72, (float) y, (float) fontSize, true, text);
    }
}
