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
 *       into clusters that are more than thin lines (a table of rules is not a figure), and clusters joined
 *       into one figure by gaps of up to {@link #MAX_GAP} font sizes (a network diagram in columns, a column
 *       cut by text). One piece overlaps the caption horizontally and is at most that gap away; the figure
 *       is wholly on one side of the caption's baseline and does not contain it (a page background does).</li>
 * </ul>
 * The lines that start in the figure, or between it and the caption, are removed; the caption stays.
 * Figures without a caption keep their text, and so do pages that paint more than {@link #MAX_BOXES} boxes.
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
    /**
     * Pages that paint more boxes are skipped: joining boxes into drawings is quadratic, and an untrusted PDF
     * can paint tens of thousands of tiny boxes in a few kilobytes. arXiv figures paint at most 419 per page.
     */
    private static final int MAX_BOXES = 10_000;

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
        Map<Integer, List<Figure>> figuresByPage = new HashMap<>();
        Map<Integer, List<PageGraphics.Box>> areasByPage = new HashMap<>();
        Set<Line> captionLines = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<Line> paragraph : Paragraphs.group(lines)) {
            Line caption = paragraph.get(0);
            if (!isCaption(caption)) {
                continue;
            }
            captionLines.addAll(paragraph);
            float size = caption.fontSize();
            float gap = MAX_GAP * size;
            // once per page, with the gap of its first caption: captions of one document share a font size
            List<Figure> figures = figuresByPage.computeIfAbsent(caption.page(),
                    page -> figures(clusters(graphicsByPage.getOrDefault(page, List.of())), gap));
            float captionTop = caption.pageY() - size;
            float captionEnd = paragraph.get(paragraph.size() - 1).pageY();
            List<PageGraphics.Box> areas = new ArrayList<>();
            for (Figure figure : figures) {
                PageGraphics.Box box = figure.box();
                if (!box.contains(caption.pageX(), caption.pageY()) && box.bottom() <= caption.pageY()
                        && figure.parts().stream().anyMatch(part -> overlaps(part, caption)
                                && captionTop - part.bottom() <= gap)) {
                    PageGraphics.Box grown = box.grow(MARGIN * size);
                    // down to the caption's baseline: labels sit just above it, the caption lines are kept anyway
                    areas.add(new PageGraphics.Box(grown.left(), grown.top(), grown.right(), caption.pageY()));
                }
            }
            if (areas.isEmpty()) {
                for (Figure figure : figures) {
                    PageGraphics.Box box = figure.box();
                    if (!box.contains(caption.pageX(), caption.pageY()) && box.top() >= captionEnd
                            && figure.parts().stream().anyMatch(part -> overlaps(part, caption)
                                    && part.top() - captionEnd <= gap)) {
                        PageGraphics.Box grown = box.grow(MARGIN * size);
                        areas.add(new PageGraphics.Box(grown.left(), captionEnd, grown.right(), grown.bottom()));
                    }
                }
            }
            areasByPage.computeIfAbsent(caption.page(), page -> new ArrayList<>()).addAll(areas);
        }
        if (areasByPage.isEmpty()) {
            return lines;
        }
        return lines.stream().filter(line -> line.isTable() || captionLines.contains(line)
                || areasByPage.getOrDefault(line.page(), List.of()).stream()
                        .noneMatch(area -> area.contains(line.pageX(), line.pageY()))).toList();
    }

    private static boolean isCaption(Line line) {
        return !line.rotated() && !line.isTable() && CAPTION.matcher(line.text()).find();
    }

    /** Whether the box is in the same column as the caption. */
    private static boolean overlaps(PageGraphics.Box box, Line caption) {
        return box.left() < caption.pageX() + caption.width() && box.right() > caption.pageX();
    }

    /** Painted boxes joined into drawings; {@code drawing} means at least one box is more than a thin line. */
    private record Cluster(PageGraphics.Box box, boolean drawing) {
    }

    /** Drawings that are pieces of one figure, and the box around them. */
    private record Figure(List<PageGraphics.Box> parts, PageGraphics.Box box) {

        Figure(List<PageGraphics.Box> parts) {
            this(parts, parts.stream().reduce(PageGraphics.Box::union).orElseThrow());
        }
    }

    /** Drawings chained by gaps of at most {@code gap} points; quadratic, like {@link #clusters}. */
    private static List<Figure> figures(List<Cluster> clusters, float gap) {
        List<List<PageGraphics.Box>> groups = new ArrayList<>();
        for (Cluster cluster : clusters) {
            if (!cluster.drawing()) {
                continue;
            }
            List<PageGraphics.Box> joined = new ArrayList<>(List.of(cluster.box()));
            for (Iterator<List<PageGraphics.Box>> it = groups.iterator(); it.hasNext(); ) {
                List<PageGraphics.Box> group = it.next();
                if (group.stream().anyMatch(part -> part.near(cluster.box(), gap))) {
                    joined.addAll(group);
                    it.remove();
                }
            }
            groups.add(joined);
        }
        return groups.stream().map(Figure::new).toList();
    }

    /** Quadratic in the boxes of one page, hence {@link #MAX_BOXES}; none for a page above it. */
    private static List<Cluster> clusters(List<PageGraphics.Box> boxes) {
        List<Cluster> clusters = new ArrayList<>();
        if (boxes.size() > MAX_BOXES) {
            return clusters;
        }
        for (PageGraphics.Box box : boxes) {
            Cluster joined = new Cluster(box, !box.thin());
            boolean merged = true;
            while (merged) {
                merged = false;
                for (int i = 0; i < clusters.size(); i++) {
                    Cluster other = clusters.get(i);
                    if (other.box().near(joined.box(), JOIN)) {
                        joined = new Cluster(joined.box().union(other.box()), joined.drawing() || other.drawing());
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
