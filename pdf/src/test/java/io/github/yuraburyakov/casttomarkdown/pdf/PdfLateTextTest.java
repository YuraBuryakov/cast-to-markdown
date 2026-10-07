package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * WeasyPrint (RFC 9562) draws the text of links and the numbers of list items after the rest of the line,
 * so PDFBox returns them as separate lines; they go back to their place in the line.
 */
class PdfLateTextTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();
    private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @Test
    void citationAndItemNumberDrawnLaterGoBackIntoTheLine() throws IOException {
        // the line leaves a gap for the citation, the number stands 2.6 pt left of the text, as in RFC 9562
        float x = 90;
        float citation = x + width("Many details specified in ");
        float after = citation + width("[RFC4122] ");
        Path pdf = TestPdf.builder()
                .page()
                .line(x, 700, "Many details specified in")
                .line(after, 700, "involved trade-offs")
                .line(x, 686, "are not needed.")
                .line(x - 2.6f - width("5."), 700, "5.")
                .line(citation, 700, "[RFC4122]")
                .writeTo(dir.resolve("late.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("5. Many details specified in [RFC4122] involved trade-offs\nare not needed.\n");
    }

    @Test
    void textFarRightOfTheLineStaysApart() throws IOException {
        // RFC 9562 title block: "Stream:" and its value 48 pt to the right come as two lines
        Path pdf = TestPdf.builder()
                .page()
                .line(72, 700, "Stream:")
                .line(72, 686, "RFC:")
                .line(72 + width("Stream:") + 48, 700, "Internet Engineering Task Force")
                .writeTo(dir.resolve("apart.pdf"));

        assertThat(converter.convert(pdf).markdown()).doesNotContain("Stream: Internet");
    }

    @Test
    void rowLabelLeftOfTheLineStaysApart() throws IOException {
        // arXiv 1712.01208 table: "Learned" drawn after its row, 3.4 pt left of it; only list numbers join there
        float x = 102;
        Path pdf = TestPdf.builder()
                .page()
                .line(x, 700, "2nd stage models: 10k")
                .line(x, 686, "2nd stage models: 50k")
                .line(x - 3.4f - width("Learned"), 700, "Learned")
                .writeTo(dir.resolve("label.pdf"));

        assertThat(converter.convert(pdf).markdown()).doesNotContain("Learned 2nd");
    }

    private float width(String text) throws IOException {
        return font.getStringWidth(text) / 1000 * 12;
    }
}
