package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Groups lines into paragraphs. Done here, not by PDFBox: PDFBox compares line gaps with the glyph
 * height it reports, and for some fonts that height is about a third of the font size, which makes
 * every line a separate paragraph.
 */
final class Paragraphs {

    /** A gap larger than this many typical line pitches starts a new paragraph. */
    private static final float PARAGRAPH_GAP = 1.3f;
    /** Typical line pitch, in font sizes, is clamped to this range; also used when there is no sample. */
    private static final float MIN_PITCH = 0.8f;
    private static final float MAX_PITCH = 1.5f;
    private static final float DEFAULT_PITCH = 1.2f;
    /** A horizontal shift larger than this many font sizes counts as an indent. */
    private static final float INDENT = 0.5f;
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*([" + Lists.BULLETS + "*-]|\\d+[.)])\\s");
    /** End of a sentence or of a heading, list lead-in or quotation. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?:;\"”’)\\]]$");
    /**
     * A sentence cut by the page end fills its last line; shorter lines are labels, dates and the like
     * ("Note", "August 2026") that only happen to lack a full stop.
     */
    private static final int FULL_LINE = 40;
    /** A table of contents line: "Introduction ........ 32". */
    private static final Pattern TOC_ENTRY = Pattern.compile("(\\.\\s?){4,}\\s*\\d+\\s*$");

    private Paragraphs() {
    }

    /**
     * A new paragraph starts on a new page (unless a sentence goes on there), when the text moves up (next column), when the font size
     * changes, after a bold numbered heading line, after a gap larger than usual for that font size,
     * and at a first-line indent.
     */
    static List<List<Line>> group(List<Line> lines) {
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

    private static boolean startsParagraph(Line previous, Line line, Line next, Map<Integer, Float> pitches) {
        if (line.isTable() || previous.isTable()) {
            return true;
        }
        if (line.page() != previous.page()) {
            return !continuesOnNextPage(previous, line);
        }
        if (line.y() <= previous.y() || line.sizeKey() != previous.sizeKey()) {
            return true;
        }
        // A bold numbered heading of body size followed by regular text without extra space (LibreOffice
        // statutes: "\u00a7 1 Name" then "(1) The name ..."). Not after any bold line: in Word glossaries the
        // first line of a definition is mostly bold, and its paragraph must stay whole.
        if (previous.bold() && !line.bold() && Headings.startsWithSectionNumber(previous.text().strip())) {
            return true;
        }
        float size = line.fontSize();
        float pitch = Math.max(MIN_PITCH * size,
                Math.min(MAX_PITCH * size, pitches.getOrDefault(line.sizeKey(), DEFAULT_PITCH * size)));
        if (line.y() - previous.y() > PARAGRAPH_GAP * pitch) {
            return true;
        }
        // First-line indent: this line is shifted right, the next one returns to the previous margin.
        // A list item's hanging indent looks the same, so lines after a list marker are excluded.
        float indent = INDENT * size;
        // The next line in another font is another paragraph: the second line of a centred title is not
        // indented (arXiv 1810.04805, the authors after it start at the title's left edge).
        return next != null
                && next.page() == line.page()
                && next.sizeKey() == line.sizeKey()
                && next.y() > line.y()
                && line.x() > previous.x() + indent
                && Math.abs(next.x() - previous.x()) < indent
                && !LIST_ITEM.matcher(previous.text()).find();
    }

    /**
     * A sentence cut by the page end: the first line of the next page goes on in the same style from the
     * same left edge, and the last line of the page does not end a sentence. Page headers and footers
     * are removed before, so the two lines follow each other.
     * ponytail: a footnote at the bottom of the page sits between them and keeps them apart.
     */
    private static boolean continuesOnNextPage(Line previous, Line line) {
        return line.page() == previous.page() + 1
                && line.sizeKey() == previous.sizeKey()
                && line.bold() == previous.bold()
                && Math.abs(line.x() - previous.x()) < INDENT * line.fontSize()
                && previous.text().strip().length() >= FULL_LINE
                && !SENTENCE_END.matcher(previous.text().strip()).find()
                && !TOC_ENTRY.matcher(previous.text()).find()
                && !LIST_ITEM.matcher(line.text()).find();
    }

    /** Median distance between consecutive lines of the same font size, per font size. */
    private static Map<Integer, Float> typicalPitches(List<Line> lines) {
        Map<Integer, List<Float>> samples = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            Line previous = lines.get(i - 1);
            Line line = lines.get(i);
            if (!line.isTable() && !previous.isTable()
                    && line.page() == previous.page() && line.y() > previous.y() && line.sizeKey() == previous.sizeKey()) {
                samples.computeIfAbsent(line.sizeKey(), k -> new ArrayList<>()).add(line.y() - previous.y());
            }
        }
        Map<Integer, Float> medians = new HashMap<>();
        samples.forEach((key, gaps) -> {
            gaps.sort(null);
            medians.put(key, gaps.get(gaps.size() / 2));
        });
        return medians;
    }
}
