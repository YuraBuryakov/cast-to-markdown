package io.github.yuraburyakov.casttomarkdown;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Builds small PDFs for tests, so tests do not depend on binary fixtures.
 *
 * <pre>{@code
 * TestPdf.builder()
 *     .page()
 *     .line(720, "First line")
 *     .writeTo(file);
 * }</pre>
 *
 * <p>{@code y} is measured from the bottom of the page in points (a US Letter page is 792 points high).
 */
final class TestPdf {

    private static final float LEFT_MARGIN = 72;
    private static final float FONT_SIZE = 12;

    private final List<List<Line>> pages = new ArrayList<>();

    private TestPdf() {
    }

    static TestPdf builder() {
        return new TestPdf();
    }

    TestPdf page() {
        pages.add(new ArrayList<>());
        return this;
    }

    TestPdf line(float y, String text) {
        if (pages.isEmpty()) {
            throw new IllegalStateException("Call page() before line()");
        }
        pages.get(pages.size() - 1).add(new Line(y, text));
        return this;
    }

    Path writeTo(Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<Line> lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    for (Line line : lines) {
                        content.beginText();
                        content.setFont(font, FONT_SIZE);
                        content.newLineAtOffset(LEFT_MARGIN, line.y());
                        content.showText(line.text());
                        content.endText();
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    private record Line(float y, String text) {
    }
}
