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
        for (List<Line> paragraph : captions) {
            captionLines.addAll(paragraph);
            Line caption = paragraph.get(0);
            if (captionsByPage.get(caption.page()) > MAX_CAPTIONS) {
                continue;
            }
            List<PageGraphics.Box> drawings = drawingsByPage.computeIfAbsent(caption.page(),
                    page -> drawings(graphicsByPage.getOrDefault(page, List.of())));
            areasByPage.computeIfAbsent(caption.page(), page -> new ArrayList<>()).addAll(areas(paragraph, drawings));
        }
        if (areasByPage.isEmpty()) {
            return lines;
        }
        return lines.stream().filter(line -> line.isTable() || captionLines.contains(line)
                || words(line) > MAX_LABEL_WORDS
                || areasByPage.getOrDefault(line.page(), List.of()).stream()
                        .noneMatch(area -> area.contains(line.pageX(), line.pageY()))).toList();
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
