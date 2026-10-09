package io.github.yuraburyakov.casttomarkdown.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Text inside figures. In untagged PDFs (LaTeX) the labels of a vector drawing are ordinary text: a network
 * diagram becomes hundreds of lines such as {@code 3x3 conv, 64}. A figure is recognized only by its caption,
 * so that nothing else is ever removed:
 * <ul>
 *   <li>a paragraph starts with {@code Figure N.}, {@code Figure N:} or {@code Fig. N.};</li>
 *   <li>right above it (or, when there is none above, right below it) is a drawing: painted boxes joined
 *       into clusters that are more than thin lines (a table of rules is not a figure), in the caption's
 *       column (overlapping it horizontally), and joined into one figure by gaps of up to {@link #MAX_GAP}
 *       font sizes (a network diagram in columns, a column cut by text). One piece is at most that gap away;
 *       the figure is wholly on one side of the caption's baseline and does not contain it (a page
 *       background does).</li>
 * </ul>
 * The short lines (labels, at most {@link #MAX_LABEL_WORDS} words) that start in the figure, beside it within
 * the caption's width, or between it and the caption, are removed; the caption and longer lines stay: a
 * table drawn as a figure keeps its rows, and a wrong figure area can never take a sentence.
 * Figures without a caption keep their text, and so do pages that paint more than {@link PageGraphics#MAX_BOXES}
 * boxes, or have more than {@link #MAX_DRAWINGS} drawings or {@link #MAX_CAPTIONS} captions.
 */
final class Figures {

    private static final Pattern CAPTION = Pattern.compile("^\\s*(Figure|Fig\\.)\\s*\\d+[.:]");
    /** Painted boxes closer than this many points belong to one drawing. */
    private static final float JOIN = 3;
    /** Labels around a drawing (axis numbers) reach this many caption font sizes beyond it. */
    private static final float MARGIN = 1;
    /**
     * A drawing at most this many caption font sizes away from the caption belongs to it, and drawings at
     * most this far apart are pieces of one figure.
     */
    private static final float MAX_GAP = 3;
    /** Only lines of at most this many words are labels; longer lines (table rows, sentences) always stay. */
    private static final int MAX_LABEL_WORDS = 8;
    /** A table drawn as a figure has at least this many rows of numbers in a run (see {@link #isTable}). */
    private static final int TABLE_ROWS = 3;
    /** Rows of a table follow each other at most this many font sizes apart. */
    private static final float ROW_PITCH = 2;
    /** A number in a table cell: digits with a decimal point, a sign, a factor or a percent, in brackets. */
    private static final Pattern NUMBER = Pattern.compile("[(\\[]?[-+]?\\d[\\d.,]*[x%]?[)\\]]?[,;]?");
    /**
     * A title of a drawing is at least this many times larger than the caption: a section heading of body size
     * just above a figure stays.
     */
    private static final float TITLE_SIZE = 1.5f;
    /** A title of a drawing stands at most this many of its own font sizes above the drawing. */
    private static final float TITLE_GAP = 2;
    /**
     * Pages with more drawings or captions are left as they are: the drawings are grouped again for every
     * caption's column, which is quadratic. arXiv pages have at most 7 drawings and 3 captions.
     */
    private static final int MAX_DRAWINGS = 200;
    private static final int MAX_CAPTIONS = 50;

    private Figures() {
    }

    /** The lines without the text of captioned figures; graphics are read only on pages with a caption. */
    static List<Line> remove(PDDocument document, List<Line> lines) throws IOException {
        Set<Integer> pages = new TreeSet<>();
        for (Line line : lines) {
            if (isCaption(line)) {
                pages.add(line.page());
            }
        }
        Map<Integer, List<PageGraphics.Box>> graphics = new HashMap<>();
        for (int page : pages) {
            PDPage pdPage = document.getPage(page - 1);
            // rotated pages: line positions are not in page coordinates there
            graphics.put(page, pdPage.getRotation() % 360 == 0 ? PageGraphics.of(pdPage) : List.of());
        }
        return remove(lines, graphics);
    }

    /** As {@link #remove(PDDocument, List)}, with the painted boxes of each page given. */
    static List<Line> remove(List<Line> lines, Map<Integer, List<PageGraphics.Box>> graphicsByPage) {
        List<List<Line>> captions = new ArrayList<>();
        Map<Integer, Integer> captionsByPage = new HashMap<>();
        for (List<Line> paragraph : Paragraphs.group(lines)) {
            if (isCaption(paragraph.get(0))) {
                captions.add(paragraph);
                captionsByPage.merge(paragraph.get(0).page(), 1, Integer::sum);
            }
        }
        Map<Integer, List<PageGraphics.Box>> drawingsByPage = new HashMap<>();
        Map<Integer, List<PageGraphics.Box>> areasByPage = new HashMap<>();
        Set<Line> captionLines = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<PageGraphics.Box, Float> captionSizes = new HashMap<>();
        for (List<Line> paragraph : captions) {
            captionLines.addAll(paragraph);
            Line caption = paragraph.get(0);
            if (captionsByPage.get(caption.page()) > MAX_CAPTIONS) {
                continue;
            }
            List<PageGraphics.Box> drawings = drawingsByPage.computeIfAbsent(caption.page(),
                    page -> drawings(graphicsByPage.getOrDefault(page, List.of())));
            List<PageGraphics.Box> areas = areas(paragraph, drawings);
            areas.forEach(area -> captionSizes.put(area, caption.fontSize()));
            areasByPage.computeIfAbsent(caption.page(), page -> new ArrayList<>()).addAll(areas);
        }
        if (areasByPage.isEmpty()) {
            return lines;
        }
        // a table drawn as a figure keeps all its text: header rows and group labels are no axis labels
        areasByPage.replaceAll((page, areas) -> areas.stream().filter(area -> !isTable(page, area, lines)).toList());
        Set<Line> rowLabels = rowLabels(lines);
        return lines.stream().filter(line -> line.isTable() || captionLines.contains(line)
                || words(line) > MAX_LABEL_WORDS || rowLabels.contains(line)
                || areasByPage.getOrDefault(line.page(), List.of()).stream()
                        .noneMatch(area -> area.contains(line.pageX(), line.pageY()) || titleOf(area, line, captionSizes.get(area)))).toList();
    }

    /** The areas of the figure above the caption, else below it; empty when there is none. */
    private static List<PageGraphics.Box> areas(List<Line> paragraph, List<PageGraphics.Box> drawings) {
        Line caption = paragraph.get(0);
        float size = caption.fontSize();
        float gap = MAX_GAP * size;
        float captionTop = caption.pageY() - size;
        float captionEnd = paragraph.get(paragraph.size() - 1).pageY();
        float captionLeft = Float.MAX_VALUE;
        float captionRight = -Float.MAX_VALUE;
        for (Line line : paragraph) {
            captionLeft = Math.min(captionLeft, line.pageX());
            captionRight = Math.max(captionRight, line.pageX() + line.width());
        }
        float left = captionLeft;
        float right = captionRight;
        // the drawings of the caption's column only: a figure in the next column is another figure
        List<PageGraphics.Box> column = drawings.stream()
                .filter(drawing -> drawing.left() < right && drawing.right() > left).toList();
        List<List<PageGraphics.Box>> figures = chained(column, gap);
        List<PageGraphics.Box> areas = new ArrayList<>();
        for (List<PageGraphics.Box> figure : figures) {
            PageGraphics.Box box = figure.stream().reduce(PageGraphics.Box::union).orElseThrow();
            if (!box.contains(caption.pageX(), caption.pageY()) && box.bottom() <= caption.pageY()
                    && figure.stream().anyMatch(part -> captionTop - part.bottom() <= gap)) {
                PageGraphics.Box grown = box.grow(MARGIN * size);
                // down to the caption's baseline: labels sit just above it, the caption lines are kept anyway
                areas.add(new PageGraphics.Box(Math.min(grown.left(), left), grown.top(),
                        Math.max(grown.right(), right), caption.pageY()));
            }
        }
        if (areas.isEmpty()) {
            for (List<PageGraphics.Box> figure : figures) {
                PageGraphics.Box box = figure.stream().reduce(PageGraphics.Box::union).orElseThrow();
                if (!box.contains(caption.pageX(), caption.pageY()) && box.top() >= captionEnd
                        && figure.stream().anyMatch(part -> part.top() - captionEnd <= gap)) {
                    PageGraphics.Box grown = box.grow(MARGIN * size);
                    areas.add(new PageGraphics.Box(Math.min(grown.left(), left), captionEnd,
                            Math.max(grown.right(), right), grown.bottom()));
                }
            }
        }
        return areas;
    }

    /**
     * Short lines on the baseline of a longer line, left of it: the labels of the rows of a table drawn as a
     * figure ("page size: 64" in arXiv 1712.01208 Figure 4), which stay with their rows.
     * ponytail: compares the lines of a page by baseline in a map; one bucket per tenth of a point.
     */
    private static Set<Line> rowLabels(List<Line> lines) {
        Map<Long, List<Line>> rows = new HashMap<>();
        for (Line line : lines) {
            if (!line.rotated() && words(line) > MAX_LABEL_WORDS) {
                rows.computeIfAbsent(baseline(line), k -> new ArrayList<>()).add(line);
            }
        }
        Set<Line> labels = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Line line : lines) {
            if (!line.rotated() && words(line) <= MAX_LABEL_WORDS
                    && rows.getOrDefault(baseline(line), List.of()).stream()
                            .anyMatch(row -> row.page() == line.page() && line.pageX() + line.width() < row.pageX())) {
                labels.add(line);
            }
        }
        return labels;
    }

    /**
     * Whether the area holds a table drawn as a figure: at least {@link #TABLE_ROWS} long lines of mostly numbers
     * that follow each other at most {@link #ROW_PITCH} font sizes apart (arXiv 1712.01208 Figures 4 and 6). The
     * rows of ticks of plots are numbers too, but a plot's height apart; the token rows of BERT Figure 1 are words.
     */
    private static boolean isTable(int page, PageGraphics.Box area, List<Line> lines) {
        List<Line> rows = lines.stream()
                .filter(line -> line.page() == page && !line.rotated() && !line.isTable() && words(line) > MAX_LABEL_WORDS
                        && area.contains(line.pageX(), line.pageY()) && mostlyNumbers(line))
                .sorted(java.util.Comparator.comparingDouble(Line::pageY)).toList();
        int run = 1;
        for (int i = 1; i < rows.size(); i++) {
            Line above = rows.get(i - 1);
            Line row = rows.get(i);
            run = row.pageY() - above.pageY() <= ROW_PITCH * row.fontSize() ? run + 1 : 1;
            if (run >= TABLE_ROWS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether more than half the words of the line are numbers ({@code 1247}, {@code 13.11}, {@code (4.00x)},
     * {@code (52%)}); not {@code 3x3} or {@code E1}, the boxes of a network diagram and the tokens of BERT.
     */
    private static boolean mostlyNumbers(Line line) {
        String[] words = line.text().strip().split("\\s+");
        return 2 * java.util.Arrays.stream(words).filter(word -> NUMBER.matcher(word).matches()).count() > words.length;
    }

    private static long baseline(Line line) {
        return (long) line.page() << 32 | Math.round(line.pageY() * 10) & 0xffffffffL;
    }

    /**
     * Whether the line is a title of the drawing in a font larger than its caption, in the area or above it by
     * at most {@link #TITLE_GAP} of its own font sizes, over it even when it starts a point left of it (arXiv 1706.03762: "Input-Input Layer5" in 19 pt, 30 pt above
     * the attention plots).
     */
    private static boolean titleOf(PageGraphics.Box area, Line line, float captionSize) {
        return !line.rotated() && line.fontSize() >= TITLE_SIZE * captionSize && line.pageY() <= area.bottom() && area.top() - line.pageY() <= TITLE_GAP * line.fontSize()
                // over the drawing: it may start a point left of it (page 15 there)
                && line.pageX() <= area.right() && line.pageX() + line.width() >= area.left();
    }

    /** The words of the line, by its word list or else by its text. */
    private static int words(Line line) {
        return line.words().isEmpty() ? line.text().strip().split("\\s+").length : line.words().size();
    }

    /** The drawing clusters of a page; none when there are more than {@link #MAX_DRAWINGS}. */
    private static List<PageGraphics.Box> drawings(List<PageGraphics.Box> boxes) {
        List<PageGraphics.Box> drawings = clusters(boxes).stream().filter(Cluster::drawing).map(Cluster::box)
                .toList();
        return drawings.size() > MAX_DRAWINGS ? List.of() : drawings;
    }

    private static boolean isCaption(Line line) {
        return !line.rotated() && !line.isTable() && CAPTION.matcher(line.text()).find();
    }

    /**
     * Painted boxes joined into drawings; {@code drawing} means at least one box is more than a thin line,
     * {@code parts} are the boxes.
     */
    record Cluster(PageGraphics.Box box, boolean drawing, List<PageGraphics.Box> parts) {
    }

    /** Drawings chained by gaps of at most {@code gap} points: the pieces of each figure. */
    private static List<List<PageGraphics.Box>> chained(List<PageGraphics.Box> drawings, float gap) {
        List<List<PageGraphics.Box>> groups = new ArrayList<>();
        for (PageGraphics.Box drawing : drawings) {
            List<PageGraphics.Box> joined = new ArrayList<>(List.of(drawing));
            for (Iterator<List<PageGraphics.Box>> it = groups.iterator(); it.hasNext(); ) {
                List<PageGraphics.Box> group = it.next();
                if (group.stream().anyMatch(part -> part.near(drawing, gap))) {
                    joined.addAll(group);
                    it.remove();
                }
            }
            groups.add(joined);
        }
        return groups;
    }

    /** Quadratic in the boxes of one page, hence {@link PageGraphics#MAX_BOXES}; none for a page above it. */
    static List<Cluster> clusters(List<PageGraphics.Box> boxes) {
        List<Cluster> clusters = new ArrayList<>();
        if (boxes.size() > PageGraphics.MAX_BOXES) {
            return clusters;
        }
        for (PageGraphics.Box box : boxes) {
            Cluster joined = new Cluster(box, !box.thin(), new ArrayList<>(List.of(box)));
            boolean merged = true;
            while (merged) {
                merged = false;
                for (int i = 0; i < clusters.size(); i++) {
                    Cluster other = clusters.get(i);
                    if (other.box().near(joined.box(), JOIN)) {
                        joined.parts().addAll(other.parts());
                        joined = new Cluster(joined.box().union(other.box()), joined.drawing() || other.drawing(),
                                joined.parts());
                        clusters.remove(i);
                        merged = true;
                        break;
                    }
                }
            }
            clusters.add(joined);
        }
        return clusters;
    }
}
