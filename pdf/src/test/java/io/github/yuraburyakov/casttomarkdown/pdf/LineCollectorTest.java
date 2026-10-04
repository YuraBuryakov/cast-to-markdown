package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Path;
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
    void rotatedLineKnowsWhereItIsOnThePage() throws IOException {
        // vertical text written upwards from (300, 400): 392 points from the top of a 792-point page
        Path pdf = TestPdf.builder().page().rotatedLine(300, 400, "training error").writeTo(dir.resolve("rotated.pdf"));

        Line line = lines(pdf).stream().filter(Line::rotated).findFirst().orElseThrow();

        assertThat(line.pageX()).isCloseTo(300f, within(12f));
        assertThat(line.pageY()).isCloseTo(392f, within(12f));
    }

    private static List<Line> lines(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return LineCollector.collect(document, TaggedTables.read(document)).lines();
        }
    }
}
