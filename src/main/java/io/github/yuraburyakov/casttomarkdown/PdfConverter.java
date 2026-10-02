package io.github.yuraburyakov.casttomarkdown;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Converts PDF to Markdown with Apache PDFBox.
 *
 * <p>Current output: paragraphs separated by a blank line, headings detected by font size
 * ({@code #} for the largest heading font, {@code ##} for the next and so on); pages are separated
 * by a blank line. PDFBox gives the text lines in reading order; paragraphs are detected here,
 * not by PDFBox, because PDFBox compares line gaps with the glyph height it reports, and for some
 * fonts that height is about a third of the font size, which makes every line a separate paragraph.
 * Bold headings of body size, lists and tables are not detected yet.
 * Markdown special characters are not escaped yet.
 *
 * <p>Stateless and thread-safe: a new {@link PDFTextStripper} is created for each call.
 */
final class PdfConverter {

    /** A gap larger than this many typical line pitches starts a new paragraph. */
    private static final float PARAGRAPH_GAP = 1.3f;
    /** Typical line pitch, in font sizes, is clamped to this range; also used when there is no sample. */
    private static final float MIN_PITCH = 0.8f;
    private static final float MAX_PITCH = 1.5f;
    private static final float DEFAULT_PITCH = 1.2f;
    /** A horizontal shift larger than this many font sizes counts as an indent. */
    private static final float INDENT = 0.5f;
    /** Longer paragraphs are not headings. */
    private static final int MAX_HEADING_LINES = 2;
    private static final int MAX_HEADING_LENGTH = 200;
    private static final int MAX_HEADING_LEVEL = 6;
    /** A heading has at least one real word; rotated or scattered text gives only fragments. */
    private static final Pattern WORD = Pattern.compile("\\p{L}{3}");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*([•◦▪‣*-]|\\d+[.)])\\s");

    String convert(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            LineCollector collector = new LineCollector();
            collector.getText(document);
            return normalize(toMarkdown(collector.lines));
        } catch (InvalidPasswordException e) {
            throw new DocumentConversionException("PDF is encrypted: " + path, e);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read PDF: " + path, e);
        }
    }

    /** One text line as PDFBox emits it; {@code y} grows downwards, {@code fontSize} is the largest on the line. */
    record Line(int page, float x, float y, float fontSize, String text) {
    }

    /**
     * Groups lines into paragraphs and renders them: lines of a paragraph joined with {@code \n},
     * paragraphs separated by a blank line, headings as {@code #} lines.
     */
    static String toMarkdown(List<Line> lines) {
        List<List<Line>> paragraphs = paragraphs(lines);
        boolean[] headings = headings(paragraphs);
        List<Integer> headingSizes = new ArrayList<>(); // distinct heading font sizes, largest first
        for (int i = 0; i < paragraphs.size(); i++) {
            int key = sizeKey(paragraphs.get(i).get(0));
            if (headings[i] && !headingSizes.contains(key)) {
                headingSizes.add(key);
            }
        }
        headingSizes.sort(Comparator.reverseOrder());

        StringJoiner out = new StringJoiner("\n\n");
        for (int i = 0; i < paragraphs.size(); i++) {
            List<Line> paragraph = paragraphs.get(i);
            if (headings[i]) {
                int level = Math.min(MAX_HEADING_LEVEL, headingSizes.indexOf(sizeKey(paragraph.get(0))) + 1);
                out.add("#".repeat(level) + " " + headingText(paragraph));
            } else {
                out.add(paragraph.stream().map(Line::text).collect(Collectors.joining("\n")));
            }
        }
        return out.toString();
    }

    private static List<List<Line>> paragraphs(List<Line> lines) {
        Map<Integer, Float> pitches = typicalPitches(lines);
        List<List<Line>> paragraphs = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line next = i + 1 < lines.size() ? lines.get(i + 1) : null;
            if (i == 0 || startsParagraph(lines.get(i - 1), lines.get(i), next, pitches)) {
                paragraphs.add(new ArrayList<>());
            }
            paragraphs.get(paragraphs.size() - 1).add(lines.get(i));
        }
        return paragraphs;
    }

    /**
     * A heading is a short paragraph in a font larger than the body font (the font of most characters)
     * that is followed by body text or another heading. The last condition drops large text inside
     * figures, which is followed by small figure text. Bold text of body size is not a heading yet.
     */
    private static boolean[] headings(List<List<Line>> paragraphs) {
        Map<Integer, Integer> charsBySize = new HashMap<>();
        for (List<Line> paragraph : paragraphs) {
            for (Line line : paragraph) {
                charsBySize.merge(sizeKey(line), line.text().strip().length(), Integer::sum);
            }
        }
        int body = charsBySize.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(0);

        boolean[] headings = new boolean[paragraphs.size()];
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            List<Line> paragraph = paragraphs.get(i);
            String text = headingText(paragraph);
            boolean candidate = sizeKey(paragraph.get(0)) > body
                    && paragraph.size() <= MAX_HEADING_LINES
                    && text.length() <= MAX_HEADING_LENGTH
                    && WORD.matcher(text).find();
            // ponytail: a heading that ends a page is followed by the running header/footer and is missed;
            // fixed by removing headers and footers before this step (iteration 4).
            boolean followedByText = i == paragraphs.size() - 1
                    || headings[i + 1]
                    || sizeKey(paragraphs.get(i + 1).get(0)) == body;
            headings[i] = candidate && followedByText;
        }
        return headings;
    }

    private static String headingText(List<Line> paragraph) {
        return paragraph.stream().map(line -> line.text().strip()).collect(Collectors.joining(" "));
    }

    private static boolean startsParagraph(Line previous, Line line, Line next, Map<Integer, Float> pitches) {
        if (line.page() != previous.page() || line.y() <= previous.y() || sizeKey(line) != sizeKey(previous)) {
            return true;
        }
        float size = line.fontSize();
        float pitch = Math.max(MIN_PITCH * size,
                Math.min(MAX_PITCH * size, pitches.getOrDefault(sizeKey(line), DEFAULT_PITCH * size)));
        if (line.y() - previous.y() > PARAGRAPH_GAP * pitch) {
            return true;
        }
        // First-line indent: this line is shifted right, the next one returns to the previous margin.
        // A list item's hanging indent looks the same, so lines after a list marker are excluded.
        float indent = INDENT * size;
        return next != null
                && next.page() == line.page()
                && next.y() > line.y()
                && line.x() > previous.x() + indent
                && Math.abs(next.x() - previous.x()) < indent
                && !LIST_ITEM.matcher(previous.text()).find();
    }

    /** Median distance between consecutive lines of the same font size, per font size. */
    private static Map<Integer, Float> typicalPitches(List<Line> lines) {
        Map<Integer, List<Float>> samples = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            Line previous = lines.get(i - 1);
            Line line = lines.get(i);
            if (line.page() == previous.page() && line.y() > previous.y() && sizeKey(line) == sizeKey(previous)) {
                samples.computeIfAbsent(sizeKey(line), k -> new ArrayList<>()).add(line.y() - previous.y());
            }
        }
        Map<Integer, Float> medians = new HashMap<>();
        samples.forEach((key, gaps) -> {
            gaps.sort(null);
            medians.put(key, gaps.get(gaps.size() / 2));
        });
        return medians;
    }

    /** Font size rounded to 0.5 pt, so that tiny differences do not split a paragraph. */
    private static int sizeKey(Line line) {
        return Math.round(line.fontSize() * 2);
    }

    /** Collects text lines with their position instead of writing text out. */
    private static final class LineCollector extends PDFTextStripper {

        final List<Line> lines = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private TextPosition first;
        private float fontSize;

        @Override
        protected void writeString(String string, List<TextPosition> positions) {
            for (TextPosition position : positions) {
                if (first == null) {
                    first = position;
                }
                fontSize = Math.max(fontSize, position.getFontSizeInPt());
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
                lines.add(new Line(getCurrentPageNo(), first.getXDirAdj(), first.getYDirAdj(), fontSize, text.toString()));
            }
            text.setLength(0);
            first = null;
            fontSize = 0;
        }
    }

    /**
     * Unifies line endings, removes trailing spaces, collapses runs of blank lines into one
     * and makes a non-empty result end with a single {@code \n}.
     */
    static String normalize(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        StringBuilder out = new StringBuilder(text.length());
        boolean blankLineBefore = false;
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (trimmed.isEmpty()) {
                blankLineBefore = out.length() > 0;
                continue;
            }
            if (out.length() > 0) {
                out.append(blankLineBefore ? "\n\n" : "\n");
            }
            out.append(trimmed);
            blankLineBefore = false;
        }
        return out.length() == 0 ? "" : out.append('\n').toString();
    }
}
