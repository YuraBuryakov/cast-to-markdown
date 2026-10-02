package io.github.yuraburyakov.casttomarkdown.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

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
    /** Indexes of pages that get a picture, like a scanned page. */
    private final Set<Integer> imagePages = new HashSet<>();

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

    /** Draws a picture on the current page; a page with only a picture looks like a scanned page. */
    TestPdf image() {
        if (pages.isEmpty()) {
            throw new IllegalStateException("Call page() before image()");
        }
        imagePages.add(pages.size() - 1);
        return this;
    }

    Path writeTo(Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 0; i < pages.size(); i++) {
                List<Line> lines = pages.get(i);
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    if (imagePages.contains(i)) {
                        content.drawImage(grayPicture(document), LEFT_MARGIN, 400, 200, 200);
                    }
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

    /** A 2x2 gray image built from raw bytes: java.awt is not readable from this module. */
    private static PDImageXObject grayPicture(PDDocument document) throws IOException {
        PDImageXObject picture = new PDImageXObject(
                new PDStream(document, new ByteArrayInputStream(new byte[] {0, 64, (byte) 128, (byte) 255})), null);
        picture.setWidth(2);
        picture.setHeight(2);
        picture.setBitsPerComponent(8);
        picture.setColorSpace(PDDeviceGray.INSTANCE);
        return picture;
    }

    private record Line(float y, String text) {
    }
}
