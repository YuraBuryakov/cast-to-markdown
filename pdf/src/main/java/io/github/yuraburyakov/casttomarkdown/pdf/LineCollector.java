package io.github.yuraburyakov.casttomarkdown.pdf;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequence;
import org.apache.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequenceWithProperties;
import org.apache.pdfbox.contentstream.operator.markedcontent.EndMarkedContentSequence;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Collects text lines with their position and font instead of writing text out.
 * PDFBox decides the reading order and where lines end; its paragraph markers are ignored.
 *
 * <p>For tagged tables ({@link TaggedTables}) it also follows the marked-content ids (MCID) of the text:
 * it collects the text of every table cell, and a line made only of the text of one table is replaced
 * by a placeholder line (see {@link Line#table()}) where the table goes; the other lines of the table
 * are dropped.
 */
final class LineCollector extends PDFTextStripper {

    /** Lines in reading order and the text of the table cells by cell id. */
    record Collected(List<Line> lines, Map<Integer, StringBuilder> cellText) {
    }

    private static final Pattern BOLD_FONT_NAME = Pattern.compile("(?i)bold|black|heavy|semibold|demibold");
    /** A horizontal gap larger than this share of the font size between two characters of a cell is a space. */
    private static final float WORD_GAP = 0.2f;

    private final List<Line> lines = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private TextPosition first;
    private final Map<Float, Integer> charsBySize = new HashMap<>();
    private int boldChars;
    private int chars;
    /**
     * Boldness of the font of the previous character: characters come in runs of one font.
     * Only the last font, not a map of all fonts: PDFBox caches fonts softly, and holding all of them
     * strongly raised the peak heap of a 76-page Word PDF from 25 to 31 MB.
     */
    private PDFont lastFont;
    private boolean lastFontBold;

    private final TaggedTables tables;
    /** MCIDs of the open marked-content sequences; {@code -1} for a sequence without one. */
    private final Deque<Integer> mcids = new ArrayDeque<>();
    private final Map<TextPosition, Integer> cellOfPosition = new IdentityHashMap<>();
    private final Map<Integer, StringBuilder> cellText = new HashMap<>();
    private final Map<Integer, TextPosition> lastCellPosition = new HashMap<>();
    private int lineTable = -1;
    private int tableChars;
    private int otherChars;
    private boolean severalTables;

    private LineCollector(TaggedTables tables) {
        this.tables = tables;
        if (!tables.isEmpty()) {
            addOperator(new BeginMarkedContentSequenceWithProperties(this));
            addOperator(new BeginMarkedContentSequence(this));
            addOperator(new EndMarkedContentSequence(this));
        }
    }

    /** Lines of all pages in reading order, with placeholders for the tagged tables. */
    static Collected collect(PDDocument document, TaggedTables tables) throws IOException {
        LineCollector collector = new LineCollector(tables);
        collector.getText(document);
        return new Collected(placeTables(collector.lines), collector.cellText);
    }

    /** The first line of each table becomes its placeholder, the other lines of the table are dropped. */
    private static List<Line> placeTables(List<Line> lines) {
        List<Line> result = new ArrayList<>(lines.size());
        Set<Integer> placed = new HashSet<>();
        for (Line line : lines) {
            if (!line.isTable()) {
                result.add(line);
            } else if (placed.add(line.table())) {
                result.add(new Line(line.page(), line.pageHeight(), line.x(), line.y(), line.fontSize(), false, false,
                        "", line.table()));
            }
        }
        return result;
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        mcids.clear();
        // the previous page is written out already; keep only this page's positions
        cellOfPosition.clear();
        super.startPage(page);
    }

    @Override
    public void beginMarkedContentSequence(COSName tag, COSDictionary properties) {
        int mcid = properties == null ? -1 : properties.getInt(COSName.MCID, -1);
        mcids.push(mcid >= 0 || mcids.isEmpty() ? mcid : mcids.peek());
    }

    @Override
    public void endMarkedContentSequence() {
        if (!mcids.isEmpty()) {
            mcids.pop();
        }
    }

    @Override
    protected void processTextPosition(TextPosition position) {
        if (!mcids.isEmpty() && mcids.peek() >= 0) {
            int cell = tables.cellOf(TaggedTables.key(getCurrentPageNo(), mcids.peek()));
            if (cell >= 0) {
                cellOfPosition.put(position, cell);
            }
        }
        super.processTextPosition(position);
    }

    @Override
    protected void writeString(String string, List<TextPosition> positions) {
        for (TextPosition position : positions) {
            if (first == null) {
                first = position;
            }
            if (!position.getUnicode().isBlank()) {
                charsBySize.merge(position.getFontSizeInPt(), 1, Integer::sum);
                chars++;
                if (position.getFont() != lastFont) {
                    lastFont = position.getFont();
                    lastFontBold = isBold(lastFont);
                }
                boldChars += lastFontBold ? 1 : 0;
            }
            Integer cell = cellOfPosition.get(position);
            if (cell != null) {
                appendCellText(cell, position);
                int table = tables.tableOfCell(cell);
                severalTables |= lineTable >= 0 && lineTable != table;
                lineTable = table;
                tableChars++;
            } else if (!position.getUnicode().isBlank()) {
                otherChars++;
            }
        }
        text.append(string);
    }

    /** Characters come in reading order; a gap or a new line inside the cell becomes a space. */
    private void appendCellText(int cellId, TextPosition position) {
        StringBuilder cell = cellText.computeIfAbsent(cellId, k -> new StringBuilder());
        TextPosition last = lastCellPosition.put(cellId, position);
        if (last != null && (Math.abs(position.getYDirAdj() - last.getYDirAdj()) > last.getHeightDir()
                || position.getXDirAdj() - (last.getXDirAdj() + last.getWidthDirAdj())
                        > WORD_GAP * position.getFontSizeInPt())) {
            cell.append(' ');
        }
        cell.append(position.getUnicode());
    }

    @Override
    protected void writeWordSeparator() {
        text.append(getWordSeparator());
    }

    @Override
    protected void writeLineSeparator() {
        endLine();
    }

    @Override
    protected void writeParagraphEnd() {
        endLine();
    }

    @Override
    protected void writePageEnd() {
        endLine();
    }

    private void endLine() {
        if (first != null && !text.toString().isBlank()) {
            PDRectangle box = getCurrentPage().getCropBox();
            float pageHeight = getCurrentPage().getRotation() % 180 == 0 ? box.getHeight() : box.getWidth();
            float fontSize = charsBySize.isEmpty() ? first.getFontSizeInPt() : dominantSize(charsBySize);
            int table = tableChars > 0 && otherChars == 0 && !severalTables ? lineTable : -1;
            lines.add(new Line(getCurrentPageNo(), pageHeight, first.getXDirAdj(), first.getYDirAdj(), fontSize,
                    boldChars * 2 > chars, first.getDir() != 0, text.toString(), table));
        }
        text.setLength(0);
        first = null;
        charsBySize.clear();
        boldChars = 0;
        chars = 0;
        lineTable = -1;
        tableChars = 0;
        otherChars = 0;
        severalTables = false;
    }

    /**
     * The font size of most characters, the larger one on a tie. Not the largest size on the line:
     * in Word documents a single larger quote mark would turn a body line into a heading.
     */
    static float dominantSize(Map<Float, Integer> charsBySize) {
        return Collections.max(charsBySize.entrySet(),
                Map.Entry.<Float, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).getKey();
    }

    /** By the font weight in the font descriptor, or by the font name when the weight is not set. */
    private static boolean isBold(PDFont font) {
        PDFontDescriptor descriptor = font.getFontDescriptor();
        if (descriptor != null && (descriptor.isForceBold() || descriptor.getFontWeight() >= 600)) {
            return true;
        }
        return font.getName() != null && BOLD_FONT_NAME.matcher(font.getName()).find();
    }
}
