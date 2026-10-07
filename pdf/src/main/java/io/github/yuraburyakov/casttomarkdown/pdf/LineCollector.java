package io.github.yuraburyakov.casttomarkdown.pdf;

import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
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
    /** Ligatures and other presentation forms, the characters PDFBox decomposes in the text of a line. */
    private static final Pattern PRESENTATION_FORMS = Pattern.compile("[\\uFB00-\\uFDFF\\uFE70-\\uFEFF]");
    /** A horizontal gap larger than this share of the font size between two characters of a cell is a space. */
    private static final float WORD_GAP = 0.2f;
    /** A space glyph overlapped by a letter over at least this share of its width is drawn over that letter. */
    private static final float COVERED = 0.9f;

    private final List<Line> lines = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private TextPosition first;
    private TextPosition last;
    private final List<Line.Word> words = new ArrayList<>();
    private final List<List<ScientificPowers.Glyph>> wordGlyphs = new ArrayList<>();
    private boolean powerEligible;
    private final boolean untagged;
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
    private final Map<Integer, LastPosition> lastCellPosition = new HashMap<>();
    /** URI links of the current page; empty on rotated pages. */
    private final List<LinkArea> links = new ArrayList<>();
    /** Target of the link the text is in now, {@code null} outside links; its text goes to {@link #linkText}. */
    private String linkUrl;
    private final StringBuilder linkText = new StringBuilder();
    private int lineTable = -1;
    private int tableChars;
    private int otherChars;
    /**
     * The line without the characters of table cells. Used for a line that mixes both: the cell text is
     * already in the table, and the line would repeat it after the table (Chrome prints the address of
     * a link inside a cell as untagged text).
     */
    private final StringBuilder otherText = new StringBuilder();
    private boolean severalTables;

    private LineCollector(TaggedTables tables, boolean untagged) {
        this.tables = tables;
        this.untagged = untagged;
        if (!tables.isEmpty()) {
            addOperator(new BeginMarkedContentSequenceWithProperties(this));
            addOperator(new BeginMarkedContentSequence(this));
            addOperator(new EndMarkedContentSequence(this));
        }
    }

    /** Lines of all pages in reading order, with placeholders for the tagged tables. */
    static Collected collect(PDDocument document, TaggedTables tables) throws IOException {
        LineCollector collector = new LineCollector(tables, document.getDocumentCatalog().getStructureTreeRoot() == null);
        collector.getText(document);
        return new Collected(placeTables(LateText.insert(collector.lines)), collector.cellText);
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
        powerEligible = untagged && page.getRotation() % 360 == 0;
        // the previous page is written out already; keep only this page's positions
        cellOfPosition.clear();
        readLinks(page);
        super.startPage(page);
    }

    /**
     * Rectangles of the links to web addresses, in the coordinates of {@link TextPosition} (from the top left
     * of the crop box). Links inside the document (table of contents, "see section 3") are left out.
     * ponytail: rotated pages get no links; every character is tested against every link of its page.
     */
    private void readLinks(PDPage page) throws IOException {
        links.clear();
        if (page.getRotation() % 360 != 0) {
            return;
        }
        PDRectangle crop = page.getCropBox();
        for (PDAnnotation annotation : page.getAnnotations()) {
            if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action
                    && action.getURI() != null && link.getRectangle() != null) {
                PDRectangle box = link.getRectangle();
                links.add(new LinkArea(box.getLowerLeftX() - crop.getLowerLeftX(),
                        crop.getUpperRightY() - box.getUpperRightY(), box.getUpperRightX() - crop.getLowerLeftX(),
                        crop.getUpperRightY() - box.getLowerLeftY(), action.getURI()));
            }
        }
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
        List<TextPosition> kept = withoutCoveredSpaces(positions);
        if (kept.size() < positions.size() && string.equals(unicode(positions))) {
            positions = kept;
            string = unicode(kept);
        }
        for (TextPosition position : positions) {
            if (first == null) {
                first = position;
            }
            last = position;
            powerEligible &= position.getDir() == 0 && urlAt(position) == null && !cellOfPosition.containsKey(position);
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
            } else {
                otherText.append(position.getUnicode());
                otherChars += position.getUnicode().isBlank() ? 0 : 1;
            }
        }
        addWords(positions);
        appendText(string, positions);
    }

    /**
     * The characters without spaces drawn over another character: LibreOffice 7.3 draws spaces over the letters
     * of a link ({@code "http  s  ://"} for {@code https://}). A space counts as covered when a letter or digit
     * overlaps at least {@link #COVERED} of its width.
     * ponytail: compares every space with every character of the string; strings are one line at most.
     */
    private static List<TextPosition> withoutCoveredSpaces(List<TextPosition> positions) {
        List<TextPosition> kept = new ArrayList<>(positions.size());
        for (TextPosition position : positions) {
            if (!position.getUnicode().isBlank() || !covered(position, positions)) {
                kept.add(position);
            }
        }
        return kept;
    }

    private static boolean covered(TextPosition space, List<TextPosition> positions) {
        float left = space.getXDirAdj();
        float right = left + space.getWidthDirAdj();
        for (TextPosition other : positions) {
            // a letter or digit: the dots of a table of contents leader also start over the space before them
            if (Character.isLetterOrDigit(other.getUnicode().codePointAt(0))
                    && Math.abs(other.getYDirAdj() - space.getYDirAdj()) < 0.1f) {
                float overlap = Math.min(right, other.getXDirAdj() + other.getWidthDirAdj())
                        - Math.max(left, other.getXDirAdj());
                if (overlap >= COVERED * (right - left)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String unicode(List<TextPosition> positions) {
        StringBuilder text = new StringBuilder();
        for (TextPosition position : positions) {
            text.append(position.getUnicode());
        }
        return text.toString();
    }

    /** The words of a string: PDFBox passes the text between its word gaps, which may hold spaces. */
    private void addWords(List<TextPosition> positions) {
        StringBuilder word = new StringBuilder();
        List<ScientificPowers.Glyph> glyphs = new ArrayList<>();
        TextPosition start = null;
        TextPosition end = null;
        for (TextPosition position : positions) {
            if (position.getUnicode().isBlank()) {
                if (start != null) {
                    words.add(word(word, start, end));
                    wordGlyphs.add(List.copyOf(glyphs));
                }
                word.setLength(0);
                glyphs.clear();
                start = null;
            } else {
                start = start == null ? position : start;
                end = position;
                word.append(position.getUnicode());
                if (position.getUnicode().length() == 1) {
                    glyphs.add(new ScientificPowers.Glyph(position.getUnicode().charAt(0), position.getXDirAdj(),
                            position.getXDirAdj() + position.getWidthDirAdj(), position.getYDirAdj(), position.getFontSizeInPt()));
                }
            }
        }
        if (start != null) {
            words.add(word(word, start, end));
            wordGlyphs.add(List.copyOf(glyphs));
        }
    }

    private static Line.Word word(CharSequence text, TextPosition start, TextPosition end) {
        return new Line.Word(wordText(text.toString()), start.getXDirAdj(), end.getXDirAdj() + end.getWidthDirAdj());
    }

    /**
     * The word with ligatures and other presentation forms decomposed ({@code ﬁ} to {@code fi}), as PDFBox
     * does for the text of a line: the characters of a word come raw from its text positions.
     */
    static String wordText(String word) {
        return PRESENTATION_FORMS.matcher(word)
                .replaceAll(form -> Normalizer.normalize(form.group(), Normalizer.Form.NFKC));
    }

    /**
     * Appends the text, inside {@code [...](url)} where its characters lie in a link. The link is decided per
     * character when the string is exactly the characters' text, else (ligatures PDFBox split) by the first one.
     */
    private void appendText(String string, List<TextPosition> positions) {
        if (links.isEmpty()) {
            text.append(string);
            return;
        }
        int end = 0;
        for (TextPosition position : positions) {
            String unicode = position.getUnicode();
            end = end >= 0 && string.startsWith(unicode, end) ? end + unicode.length() : -1;
        }
        if (end != string.length()) {
            switchLink(positions.isEmpty() ? null : urlAt(positions.get(0)));
            currentText().append(string);
            return;
        }
        String previous = "";
        for (TextPosition position : positions) {
            String unicode = position.getUnicode();
            // the string is one word: a link box that ends inside it ("[Scheme t](url)o") does not split it
            if (!(endsWithLetterOrDigit(previous) && startsWithLetterOrDigit(unicode))) {
                switchLink(urlAt(position));
            }
            currentText().append(unicode);
            previous = unicode;
        }
    }

    private static boolean endsWithLetterOrDigit(String text) {
        return !text.isEmpty() && Character.isLetterOrDigit(text.codePointBefore(text.length()));
    }

    private static boolean startsWithLetterOrDigit(String text) {
        return !text.isEmpty() && Character.isLetterOrDigit(text.codePointAt(0));
    }

    /**
     * The link the centre of the character lies in. Only horizontal text: a link box touching the vertical
     * margin text of NIST PDFs changed it on one page, so it was no longer removed as page furniture.
     */
    private String urlAt(TextPosition position) {
        if (position.getDir() != 0) {
            return null;
        }
        float x = position.getXDirAdj() + position.getWidthDirAdj() / 2;
        float y = position.getYDirAdj() - position.getHeightDir() / 2;
        for (LinkArea link : links) {
            if (x >= link.left() && x <= link.right() && y >= link.top() && y <= link.bottom()) {
                return link.url();
            }
        }
        return null;
    }

    private StringBuilder currentText() {
        return linkUrl == null ? text : linkText;
    }

    private void switchLink(String url) {
        if (!Objects.equals(url, linkUrl)) {
            closeLink();
            linkUrl = url;
        }
    }

    private void closeLink() {
        if (linkUrl != null) {
            text.append(Markdown.link(linkText.toString(), linkUrl));
            linkText.setLength(0);
            linkUrl = null;
        }
    }

    /** Characters come in reading order; a gap or a new line inside the cell becomes a space. */
    private void appendCellText(int cellId, TextPosition position) {
        StringBuilder cell = cellText.computeIfAbsent(cellId, k -> new StringBuilder());
        LastPosition last = lastCellPosition.put(cellId, new LastPosition(position.getXDirAdj(),
                position.getYDirAdj(), position.getWidthDirAdj(), position.getHeightDir()));
        if (last != null && (Math.abs(position.getYDirAdj() - last.y()) > last.height()
                || position.getXDirAdj() - (last.x() + last.width())
                        > WORD_GAP * position.getFontSizeInPt())) {
            cell.append(' ');
        }
        cell.append(position.getUnicode());
    }

    @Override
    protected void writeWordSeparator() {
        currentText().append(getWordSeparator());
        otherText.append(getWordSeparator());
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
        closeLink();
        // strip: the separators of the left-out cell words would indent the line
        String lineText = tableChars > 0 && otherChars > 0 ? otherText.toString().strip() : text.toString();
        if (first != null && !lineText.isBlank()) {
            PDRectangle box = getCurrentPage().getCropBox();
            float pageHeight = getCurrentPage().getRotation() % 180 == 0 ? box.getHeight() : box.getWidth();
            float fontSize = charsBySize.isEmpty() ? first.getFontSizeInPt() : dominantSize(charsBySize);
            int table = tableChars > 0 && otherChars == 0 && !severalTables ? lineTable : -1;
            float width = last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj();
            lines.add(new Line(getCurrentPageNo(), pageHeight, first.getXDirAdj(), first.getYDirAdj(), fontSize,
                    boldChars * 2 > chars, first.getDir() != 0, lineText, table, width, first.getX(), first.getY(),
                    powerEligible && tableChars == 0 ? ScientificPowers.detect(words, wordGlyphs) : List.copyOf(words)));
        }
        text.setLength(0);
        first = null;
        last = null;
        words.clear();
        wordGlyphs.clear();
        powerEligible = untagged && getCurrentPage().getRotation() % 360 == 0;
        charsBySize.clear();
        boldChars = 0;
        chars = 0;
        lineTable = -1;
        tableChars = 0;
        otherChars = 0;
        otherText.setLength(0);
        severalTables = false;
    }

    /**
     * Where the previous character of a cell is: enough to decide on a space, without keeping the PDFBox
     * {@link TextPosition} (and its font and text matrix) of every cell until the end of the document.
     */
    private record LastPosition(float x, float y, float width, float height) {
    }

    /** A link rectangle in the coordinates of {@link TextPosition}: y grows downwards. */
    private record LinkArea(float left, float top, float right, float bottom, String url) {
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
