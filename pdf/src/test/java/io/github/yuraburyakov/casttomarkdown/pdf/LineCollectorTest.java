package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineCollectorTest {

    @TempDir
    Path dir;

    @Test
    void lineSizeIsTheSizeOfMostCharacters() {
        // Word 365: a glossary line of 94 characters in 12 pt with one opening quote in 14 pt.
        assertThat(LineCollector.dominantSize(Map.of(12f, 94, 14f, 1))).isEqualTo(12f);
    }

    @Test
    void largerSizeWinsOnTie() {
        assertThat(LineCollector.dominantSize(Map.of(9f, 5, 11f, 5))).isEqualTo(11f);
    }

    @Test
    void lineKnowsItsWidthAndWhereItIsOnThePage() throws IOException {
        Path pdf = TestPdf.builder().page().line(100, 700, "Hello world").writeTo(dir.resolve("width.pdf"));

        Line line = lines(pdf).get(0);

        float width = new PDType1Font(Standard14Fonts.FontName.HELVETICA).getStringWidth("Hello world") / 1000 * 12;
        assertThat(line.width()).isCloseTo(width, within(1f));
        assertThat(line.pageX()).isCloseTo(line.x(), within(0.5f));
        assertThat(line.pageY()).isCloseTo(line.y(), within(0.5f));
    }

    @Test
    void lineKnowsWhereEachWordIs() throws IOException {
        Path pdf = TestPdf.builder().page().line(100, 700, "Hello big world").writeTo(dir.resolve("words.pdf"));

        List<Line.Word> words = lines(pdf).get(0).words();

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        assertThat(words).extracting(Line.Word::text).containsExactly("Hello", "big", "world");
        assertThat(words.get(0).left()).isCloseTo(100f, within(0.5f));
        assertThat(words.get(0).right()).isCloseTo(100 + font.getStringWidth("Hello") / 1000 * 12, within(0.5f));
        assertThat(words.get(2).right()).isCloseTo(100 + font.getStringWidth("Hello big world") / 1000 * 12, within(0.5f));
    }

    @Test
    void wordsHaveLigaturesDecomposedAsTheLineText() {
        // arXiv Table 9: "reﬁnement" with the ligature U+FB01 in the text positions
        assertThat(LineCollector.wordText("reﬁnement")).isEqualTo("refinement");
        assertThat(LineCollector.wordText("3×3,")).isEqualTo("3×3,");
    }

    @Test
    void rotatedLineKnowsWhereItIsOnThePage() throws IOException {
        // vertical text written upwards from (300, 400): 392 points from the top of a 792-point page
        Path pdf = TestPdf.builder().page().rotatedLine(300, 400, "training error").writeTo(dir.resolve("rotated.pdf"));

        Line line = lines(pdf).stream().filter(Line::rotated).findFirst().orElseThrow();

        assertThat(line.pageX()).isCloseTo(300f, within(12f));
        assertThat(line.pageY()).isCloseTo(392f, within(12f));
    }

    @Test
    void spaceDrawnOverALetterIsNoSpace() throws IOException {
        // LibreOffice 7.3 (tdf-statutes.pdf): spaces over the "p" and the "s" of a link, "http  s  ://..."
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float x = 100;
        float s = x + font.getStringWidth("http") / 1000 * 12;
        float colon = s + font.getStringWidth("s") / 1000 * 12;
        Path pdf = TestPdf.builder()
                .page()
                .line(x, 700, "http")
                .line(s - 3.3f, 700, " ")
                .line(s, 700, "s")
                .line(s + 0.1f, 700, " ")
                .line(colon, 700, "://example.org")
                .writeTo(dir.resolve("covered.pdf"));

        assertThat(lines(pdf)).extracting(Line::text).containsExactly("https://example.org");
    }

    @Test
    void justifiedGapAroundASpaceGivesOneSpace() throws IOException {
        // LibreOffice 7.3 (tdf-statutes.pdf): the space character is narrower than the stretched gap
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float x = 100;
        float the = x + font.getStringWidth("charge.") / 1000 * 12 + 2;
        float foundation = the + font.getStringWidth(" The ") / 1000 * 12 + 4;
        Path pdf = TestPdf.builder()
                .page()
                .line(x, 700, "charge.")
                .line(the, 700, " The ")
                .line(foundation, 700, "foundation")
                .writeTo(dir.resolve("justified.pdf"));

        assertThat(lines(pdf)).extracting(Line::text).containsExactly("charge. The foundation");
    }

    @Test
    void slightlyStretchedLineKeepsItsFontSize() throws IOException {
        // arXiv 1706.03762 (microtype): lines stretched to 98% gave 9 pt instead of 10 and split the paragraph
        Path pdf = TestPdf.builder()
                .page()
                .line(72, 700, "A line as it is set")
                .stretchedLine(72, 686, "a line stretched a little", 98)
                .writeTo(dir.resolve("stretched.pdf"));

        assertThat(lines(pdf)).extracting(Line::fontSize).containsExactly(12f, 12f);
    }

    @Test
    void lineStartingWithARaisedNumberIsOnTheBaselineOfItsText() throws IOException {
        // arXiv 1712.01208: "5We would like to thank" with the footnote number 3 pt above the text
        Path pdf = TestPdf.builder()
                .page()
                .footnoteLine(72, 700, "5", "We would like to thank Michael Mitzenmacher")
                .writeTo(dir.resolve("footnote.pdf"));

        Line line = lines(pdf).get(0);

        assertThat(line.text()).isEqualTo("⁵We would like to thank Michael Mitzenmacher");
        assertThat(line.y()).isCloseTo(792 - 700, within(0.5f));
    }

    @Test
    void raisedFootnoteNumberIsASuperscript() throws IOException {
        // arXiv ResNet: "COCO 2015 competitions1, where", the number 6 pt on 9 pt text, 3.6 pt up
        Path pdf = TestPdf.builder()
                .page()
                .scriptLine(720, "COCO 2015 competitions", "1", 4, ", where", null)
                .scriptLine(690, "as shown", "12", 4, " before", null)
                .writeTo(dir.resolve("raised.pdf"));

        assertThat(lines(pdf)).extracting(Line::text)
                .containsExactly("COCO 2015 competitions¹, where", "as shown¹² before");
    }

    @Test
    void otherSmallDigitsStayDigits() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .scriptLine(720, "the state h", "1", -2, " of", null) // a subscript
                .scriptLine(690, "about 10", "6", 4, " steps", null) // a power: ScientificPowers decides
                .scriptLine(660, "page", "1234", 4, " on", null) // more than three digits
                .scriptLine(630, "large", "1", 1, " text", null) // hardly raised
                .line(72, 600, "competitions1, where") // the size of the text
                .scriptLine(570, "strings a", "10,000", 4, "b", null) // a part of a longer raised text
                .writeTo(dir.resolve("digits.pdf"));

        assertThat(lines(pdf)).extracting(Line::text).containsExactly("the state h1 of", "about 106 steps",
                "page1234 on", "large1 text", "competitions1, where", "strings a10,000b");
    }

    @Test
    void textOutsideThePageIsLeftOut() throws IOException {
        // arXiv 1712.01208 page 21: a URL runs past the right page edge, its final full stop is not on the page
        Path pdf = TestPdf.builder()
                .page()
                .line(72, 700, "A visible line")
                .line(620, 400, "past the edge")
                .line(600, 300, "half on the page")
                .writeTo(dir.resolve("outside.pdf"));

        // "h" is on the page, "a" crosses the edge at 612 pt and is partly drawn, the rest is beyond it
        assertThat(lines(pdf)).extracting(Line::text).containsExactly("A visible line", "ha");
    }

    @Test
    void longLineWithManySpacesTakesLinearTime() throws IOException {
        // a hostile page: one string of 60 000 characters, half of them spaces
        Path pdf = TestPdf.builder().page().line(10, 700, "a ".repeat(30_000)).writeTo(dir.resolve("long.pdf"));

        List<Line> lines = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> lines(pdf));

        assertThat(lines).hasSize(1);
    }

    private static List<Line> lines(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return LineCollector.collect(document, TaggedTables.read(document)).lines();
        }
    }
}
