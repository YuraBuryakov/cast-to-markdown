package io.github.yuraburyakov.casttomarkdown.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Tables of untagged PDFs (LaTeX, xml2rfc) drawn as a grid of rules. Without the grid the cells are plain
 * text lines and an empty cell is lost: {@code ensemble 59.0 37.4} no longer says which columns the numbers
 * are in. A table is recognized only by its caption, so that nothing else is ever turned into one:
 * <ul>
 *   <li>a paragraph starts with {@code Table N.} or {@code Table N:};</li>
 *   <li>right above it (or, when there is none above, right below it) is a cluster of thin rules only, with
 *       at least two horizontal and one vertical rule, that overlaps the caption horizontally, is at most
 *       {@link #MAX_GAP} font sizes away and does not contain it.</li>
 * </ul>
 * Columns run between the vertical rules, bands between the horizontal ones. A band is one row per text line
 * when every line in it has a word in the first column, else one row whose cells join their lines. A word
 * belongs to the column of its centre; where a row has no vertical rule between two columns, the cells are
 * merged and the text goes to the first one. The first row is the header.
 * The grid is left as text when a line has words both inside and outside it (text of the other column on
 * the same line) or it holds a tagged table, a rotated line or text outside its bands.
 */
final class RuledTables {

    private static final Pattern CAPTION = Pattern.compile("^\\s*Table\\s+\\d+[.:]");
    /** A grid at most this many caption font sizes away from the caption belongs to it. */
    private static final float MAX_GAP = 3;
    /** Rules and baselines closer than this many points are one. */
    private static final float SAME = 2;

    private RuledTables() {
    }

    /**
     * The lines with each captioned ruled table replaced by a placeholder line; the table's Markdown is
     * appended to {@code tableMarkdown}, the placeholder refers to it by index. Graphics are read only on
     * pages with a caption.
     */
    static List<Line> replace(PDDocument document, List<Line> lines, List<String> tableMarkdown) throws IOException {
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
        return replace(lines, graphics, tableMarkdown);
    }

    /** As {@link #replace(PDDocument, List, List)}, with the painted boxes of each page given. */
    static List<Line> replace(List<Line> lines, Map<Integer, List<PageGraphics.Box>> graphicsByPage,
            List<String> tableMarkdown) {
        Map<Integer, List<Figures.Cluster>> clustersByPage = new HashMap<>();
        Map<Line, Integer> placeholders = new IdentityHashMap<>();
        Set<Line> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<Line> paragraph : Paragraphs.group(lines)) {
            Line caption = paragraph.get(0);
            if (!isCaption(caption)) {
                continue;
            }
            List<PageGraphics.Box> boxes = graphicsByPage.getOrDefault(caption.page(), List.of());
            List<Figures.Cluster> clusters = clustersByPage.computeIfAbsent(caption.page(),
                    page -> Figures.clusters(boxes));
            PageGraphics.Box grid = grid(clusters, boxes, paragraph);
            if (grid == null) {
                continue;
            }
            List<Line> inGrid = linesIn(grid, caption.page(), lines, removed);
            List<List<String>> rows = inGrid == null || inGrid.isEmpty() ? null
                    : rows(inGrid, grid, rulesIn(grid, boxes));
            if (rows == null) {
                continue;
            }
            placeholders.put(inGrid.get(0), tableMarkdown.size());
            tableMarkdown.add(TableMarkdown.of(rows, false));
            removed.addAll(inGrid);
        }
        if (removed.isEmpty()) {
            return lines;
        }
        List<Line> result = new ArrayList<>(lines.size());
        for (Line line : lines) {
            Integer table = placeholders.get(line);
            if (table != null) {
                result.add(new Line(line.page(), line.pageHeight(), line.x(), line.y(), line.fontSize(), false, false,
                        "", table));
            } else if (!removed.contains(line)) {
                result.add(line);
            }
        }
        return result;
    }

    private static boolean isCaption(Line line) {
        return !line.rotated() && !line.isTable() && CAPTION.matcher(line.text()).find();
    }

    /** The nearest grid above the caption, else below it; {@code null} when there is none. */
    private static PageGraphics.Box grid(List<Figures.Cluster> clusters, List<PageGraphics.Box> boxes,
            List<Line> paragraph) {
        Line caption = paragraph.get(0);
        float gap = MAX_GAP * caption.fontSize();
        float captionTop = caption.pageY() - caption.fontSize();
        float captionEnd = paragraph.get(paragraph.size() - 1).pageY();
        PageGraphics.Box above = null;
        PageGraphics.Box below = null;
        for (Figures.Cluster cluster : clusters) {
            PageGraphics.Box box = cluster.box();
            if (cluster.drawing() || box.contains(caption.pageX(), caption.pageY())
                    || box.left() >= caption.pageX() + caption.width() || box.right() <= caption.pageX()
                    || !isGrid(rulesIn(box, boxes))) {
                continue;
            }
            if (box.bottom() <= caption.pageY() && captionTop - box.bottom() <= gap
                    && (above == null || box.bottom() > above.bottom())) {
                above = box;
            } else if (box.top() >= captionEnd && box.top() - captionEnd <= gap
                    && (below == null || box.top() < below.top())) {
                below = box;
            }
        }
        return above != null ? above : below;
    }

    private static List<PageGraphics.Box> rulesIn(PageGraphics.Box grid, List<PageGraphics.Box> boxes) {
        PageGraphics.Box area = grid.grow(0.5f);
        return boxes.stream().filter(box -> box.thin() && area.contains(box.left(), box.top())
                && area.contains(box.right(), box.bottom())).toList();
    }

    private static boolean isGrid(List<PageGraphics.Box> rules) {
        long horizontal = rules.stream().filter(RuledTables::isHorizontal).count();
        return horizontal >= 2 && rules.size() > horizontal;
    }

    private static boolean isHorizontal(PageGraphics.Box rule) {
        return rule.right() - rule.left() > rule.bottom() - rule.top();
    }

    /**
     * The lines whose words are in the grid, in document order; {@code null} when the grid cannot be a table
     * (a line half in it, a tagged table or rotated text in it, a line already in another table).
     */
    private static List<Line> linesIn(PageGraphics.Box grid, int page, List<Line> lines, Set<Line> taken) {
        List<Line> inGrid = new ArrayList<>();
        for (Line line : lines) {
            if (line.page() != page || line.pageY() <= grid.top() || line.pageY() >= grid.bottom()) {
                continue;
            }
            if (line.isTable() || line.rotated() || line.words().isEmpty()) {
                if (line.pageX() >= grid.left() && line.pageX() <= grid.right()) {
                    return null;
                }
                continue;
            }
            int inside = 0;
            for (Line.Word word : line.words()) {
                float centre = (word.left() + word.right()) / 2;
                inside += centre >= grid.left() && centre <= grid.right() ? 1 : 0;
            }
            if (inside == 0) {
                continue;
            }
            if (inside < line.words().size() || taken.contains(line)) {
                return null;
            }
            inGrid.add(line);
        }
        return inGrid;
    }

    /** One text line of the table: the words on one baseline, left to right. */
    private record TextLine(float y, float fontSize, List<Line.Word> words) {
    }

    /** The cells of the table, row by row; {@code null} when some text is outside the bands. */
    private static List<List<String>> rows(List<Line> lines, PageGraphics.Box grid, List<PageGraphics.Box> rules) {
        List<PageGraphics.Box> verticals = rules.stream().filter(rule -> !isHorizontal(rule)).toList();
        List<Float> bounds = new ArrayList<>(List.of(grid.left()));
        for (float x : merged(verticals.stream().map(rule -> (rule.left() + rule.right()) / 2).toList())) {
            if (x - grid.left() > SAME && grid.right() - x > SAME) {
                bounds.add(x);
            }
        }
        bounds.add(grid.right());
        List<Float> bands = merged(rules.stream().filter(RuledTables::isHorizontal)
                .map(rule -> (rule.top() + rule.bottom()) / 2).toList());

        List<TextLine> textLines = textLines(lines);
        List<List<String>> rows = new ArrayList<>();
        int placed = 0;
        for (int b = 0; b + 1 < bands.size(); b++) {
            float top = bands.get(b);
            float bottom = bands.get(b + 1);
            List<TextLine> band = textLines.stream().filter(line -> line.y() > top && line.y() <= bottom).toList();
            if (band.isEmpty()) {
                continue;
            }
            placed += band.size();
            boolean labelOnEveryLine = band.stream().allMatch(line -> line.words().stream()
                    .anyMatch(word -> column(word, bounds) == 0));
            if (labelOnEveryLine) {
                for (TextLine line : band) {
                    rows.add(cells(List.of(line), line.y() - line.fontSize(), line.y(), bounds, verticals));
                }
            } else {
                rows.add(cells(band, top, bottom, bounds, verticals));
            }
        }
        return placed == textLines.size() ? rows : null;
    }

    /** The cells of one row: words in reading order, merged cells to their first column. */
    private static List<String> cells(List<TextLine> lines, float top, float bottom, List<Float> bounds,
            List<PageGraphics.Box> verticals) {
        int columns = bounds.size() - 1;
        boolean[] ruled = new boolean[columns];
        for (int c = 1; c < columns; c++) {
            float x = bounds.get(c);
            ruled[c] = verticals.stream().anyMatch(rule -> Math.abs((rule.left() + rule.right()) / 2 - x) <= SAME
                    && rule.top() < bottom - 0.5f && rule.bottom() > top + 0.5f);
        }
        List<StringBuilder> cells = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            cells.add(new StringBuilder());
        }
        for (TextLine line : lines) {
            for (Line.Word word : line.words()) {
                int column = column(word, bounds);
                while (column > 0 && !ruled[column]) {
                    column--;
                }
                StringBuilder cell = cells.get(column);
                cell.append(cell.isEmpty() ? "" : " ").append(word.text());
            }
        }
        return cells.stream().map(StringBuilder::toString).toList();
    }

    private static int column(Line.Word word, List<Float> bounds) {
        float centre = (word.left() + word.right()) / 2;
        int column = 0;
        while (column + 2 < bounds.size() && centre >= bounds.get(column + 1)) {
            column++;
        }
        return column;
    }

    /** The words of the lines grouped by baseline, top to bottom, each line left to right. */
    private static List<TextLine> textLines(List<Line> lines) {
        List<Line> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparingDouble(Line::pageY));
        List<TextLine> textLines = new ArrayList<>();
        for (Line line : sorted) {
            TextLine last = textLines.isEmpty() ? null : textLines.get(textLines.size() - 1);
            if (last != null && line.pageY() - last.y() <= SAME) {
                last.words().addAll(line.words());
                last.words().sort(Comparator.comparingDouble(Line.Word::left));
            } else {
                textLines.add(new TextLine(line.pageY(), line.fontSize(), new ArrayList<>(line.words())));
            }
        }
        return textLines;
    }

    /** The values sorted, with values closer than {@link #SAME} to the previous one left out. */
    private static List<Float> merged(List<Float> values) {
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        List<Float> merged = new ArrayList<>();
        for (float value : sorted) {
            if (merged.isEmpty() || value - merged.get(merged.size() - 1) > SAME) {
                merged.add(value);
            }
        }
        return merged;
    }
}
