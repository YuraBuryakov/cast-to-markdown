package io.github.yuraburyakov.casttomarkdown.internal.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Collects text lines with their position and font instead of writing text out.
 * PDFBox decides the reading order and where lines end; its paragraph markers are ignored.
 */
final class LineCollector extends PDFTextStripper {

    private static final Pattern BOLD_FONT_NAME = Pattern.compile("(?i)bold|black|heavy|semibold|demibold");

    private final List<Line> lines = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private TextPosition first;
    private final Map<Float, Integer> charsBySize = new HashMap<>();
    private int boldChars;
    private int chars;

    private LineCollector() {
    }

    /** Lines of all pages in reading order. */
    static List<Line> collect(PDDocument document) throws IOException {
        LineCollector collector = new LineCollector();
        collector.getText(document);
        return collector.lines;
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
                boldChars += isBold(position.getFont()) ? 1 : 0;
            }
        }
        text.append(string);
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
            lines.add(new Line(getCurrentPageNo(), pageHeight, first.getXDirAdj(), first.getYDirAdj(), fontSize,
                    boldChars * 2 > chars, first.getDir() != 0, text.toString()));
        }
        text.setLength(0);
        first = null;
        charsBySize.clear();
        boldChars = 0;
        chars = 0;
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
