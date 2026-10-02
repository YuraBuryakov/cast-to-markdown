package io.github.yuraburyakov.casttomarkdown.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
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
    private static final float COLUMN_WIDTH = 160;
    private static final float ROW_HEIGHT = 16;

    /** Lines and tables of each page, in the order they were added: that is the order in the content stream. */
    private final List<List<Item>> pages = new ArrayList<>();
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

    /**
     * A tagged table on the current page, like Word or Chrome write it: a {@code Table} structure element
     * with {@code TR} rows and {@code TH} (first row) / {@code TD} cells, each cell text in marked content
     * with its MCID. The first row is drawn at {@code y}, the next rows below it.
     */
    TestPdf table(float y, String[]... rows) {
        if (pages.isEmpty()) {
            throw new IllegalStateException("Call page() before table()");
        }
        pages.get(pages.size() - 1).add(new Table(y, rows));
        return this;
    }

    Path writeTo(Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDStructureElement documentElement = null;
            if (pages.stream().flatMap(List::stream).anyMatch(Table.class::isInstance)) {
                PDStructureTreeRoot root = new PDStructureTreeRoot();
                document.getDocumentCatalog().setStructureTreeRoot(root);
                documentElement = new PDStructureElement(StandardStructureTypes.DOCUMENT, root);
                root.appendKid(documentElement);
            }
            for (int i = 0; i < pages.size(); i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    if (imagePages.contains(i)) {
                        content.drawImage(grayPicture(document), LEFT_MARGIN, 400, 200, 200);
                    }
                    int mcid = 0;
                    for (Item item : pages.get(i)) {
                        if (item instanceof Table table) {
                            mcid = drawTable(content, font, page, documentElement, table, mcid);
                        } else if (item instanceof Line line) {
                            content.beginText();
                            content.setFont(font, FONT_SIZE);
                            content.newLineAtOffset(LEFT_MARGIN, line.y());
                            content.showText(line.text());
                            content.endText();
                        }
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    private static int drawTable(PDPageContentStream content, PDType1Font font, PDPage page,
            PDStructureElement parent, Table table, int firstMcid) throws IOException {
        int mcid = firstMcid;
        PDStructureElement tableElement = new PDStructureElement(StandardStructureTypes.TABLE, parent);
        tableElement.setPage(page);
        parent.appendKid(tableElement);
        for (int r = 0; r < table.rows().length; r++) {
            PDStructureElement row = new PDStructureElement(StandardStructureTypes.TR, tableElement);
            tableElement.appendKid(row);
            for (int c = 0; c < table.rows()[r].length; c++) {
                String type = r == 0 ? StandardStructureTypes.TH : StandardStructureTypes.TD;
                PDStructureElement cell = new PDStructureElement(type, row);
                cell.setPage(page);
                row.appendKid(cell);
                COSDictionary properties = new COSDictionary();
                properties.setInt(COSName.MCID, mcid++);
                content.beginMarkedContent(COSName.getPDFName(type), PDPropertyList.create(properties));
                content.beginText();
                content.setFont(font, FONT_SIZE);
                content.newLineAtOffset(LEFT_MARGIN + c * COLUMN_WIDTH, table.y() - r * ROW_HEIGHT);
                content.showText(table.rows()[r][c]);
                content.endText();
                content.endMarkedContent();
                cell.appendKid(new PDMarkedContent(COSName.getPDFName(type), properties));
            }
        }
        return mcid;
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

    private sealed interface Item permits Line, Table {
    }

    private record Line(float y, String text) implements Item {
    }

    private record Table(float y, String[][] rows) implements Item {
    }
}
