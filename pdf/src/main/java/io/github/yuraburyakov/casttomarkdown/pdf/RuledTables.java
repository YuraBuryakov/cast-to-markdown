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
 *   <li>a line starts with {@code Table N.} or {@code Table N:} and starts a paragraph, or is more than
 *       {@link #CAPTION_GAP} font sizes below the line before it (a caption right under the table rows, in their
 *       font, can end up in their paragraph);</li>
 *   <li>right above it (or, when there is none above, right below it) is a cluster of thin rules only, with
 *       at least two horizontal and one vertical rule, that overlaps the caption horizontally, is at most
 *       {@link #MAX_GAP} font sizes away and does not contain it.</li>
 * </ul>
 * Columns run between the vertical rules, bands between the horizontal ones. A band is one row per text line
 * when every line in it has a word in the first column, else one row whose cells join their lines. A word
 * belongs to the column of its centre; where a row has no vertical rule between two columns, the cells are
 * merged and the text goes to the first one. The first row is the header.
 * The grid is left as text when a line has words both inside and outside it (text of the other column on
 * the same line) or it holds a tagged table, a rotated line or text outside its bands, and a cluster of more
 * than {@link #MAX_RULES} rules is not taken for a grid.
 */
final class RuledTables {

    private static final Pattern CAPTION = Pattern.compile("^\\s*Table\\s+\\d+[.:]");
    /** A caption line at least this many font sizes below the previous line starts the caption. */
    private static final float CAPTION_GAP = 1.5f;
    /** A grid at most this many caption font sizes away from the caption belongs to it. */
    private static final float MAX_GAP = 3;
    /** Rules and baselines closer than this many points are one. */
    private static final float SAME = 2;
    /**
     * A cluster with more rules is no table: untrusted input can draw thousands of rules, and the work per
     * table grows with them. arXiv tables have at most about 140.
     */
    private static final int MAX_RULES = 1_000;

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
        Map<Integer, List<Grid>> gridsByPage = new HashMap<>();
        Map<Integer, List<Line>> linesByPage = new HashMap<>();
        for (Line line : lines) {
            linesByPage.computeIfAbsent(line.page(), page -> new ArrayList<>()).add(line);
        }
        Map<Line, Integer> placeholders = new IdentityHashMap<>();
        Set<Line> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<Line> paragraph : captions(lines)) {
            Line caption = paragraph.get(0);
            List<Grid> grids = gridsByPage.computeIfAbsent(caption.page(),
                    page -> grids(graphicsByPage.getOrDefault(page, List.of())));
            Grid grid = grid(grids, paragraph);
            if (grid == null) {
                continue;
            }
            List<Line> inGrid = linesIn(grid.box(), linesByPage.get(caption.page()), removed);
            List<List<String>> rows = inGrid == null || inGrid.isEmpty() ? null : rows(inGrid, grid);
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

    /** The captions with the rest of their paragraph. */
    private static List<List<Line>> captions(List<Line> lines) {
        List<List<Line>> captions = new ArrayList<>();
        for (List<Line> paragraph : Paragraphs.group(lines)) {
            for (int i = 0; i < paragraph.size(); i++) {
                Line line = paragraph.get(i);
                Line previous = i == 0 ? null : paragraph.get(i - 1);
                if (isCaption(line) && (previous == null || previous.page() != line.page()
                        || line.pageY() - previous.pageY() > CAPTION_GAP * line.fontSize())) {
                    captions.add(paragraph.subList(i, paragraph.size()));
                    break;
                }
            }
        }
        return captions;
    }

    private static boolean isCaption(Line line) {
        return !line.rotated() && !line.isTable() && CAPTION.matcher(line.text()).find();
    }

    /** A cluster of thin rules with at least two horizontal and one vertical rule. */
    private record Grid(PageGraphics.Box box, List<PageGraphics.Box> horizontals, List<PageGraphics.Box> verticals) {
    }

    /** The grids of a page, found once per page. */
    private static List<Grid> grids(List<PageGraphics.Box> boxes) {
        List<Grid> grids = new ArrayList<>();
        for (Figures.Cluster cluster : Figures.clusters(boxes)) {
            if (cluster.drawing() || cluster.parts().size() > MAX_RULES) {
                continue;
            }
            List<PageGraphics.Box> horizontals = cluster.parts().stream().filter(RuledTables::isHorizontal).toList();
            List<PageGraphics.Box> verticals = cluster.parts().stream().filter(rule -> !isHorizontal(rule)).toList();
            if (horizontals.size() >= 2 && !verticals.isEmpty()) {
                grids.add(new Grid(cluster.box(), horizontals, verticals));
            }
        }
        return grids;
    }

    /** The nearest grid above the caption, else below it; {@code null} when there is none. */
    private static Grid grid(List<Grid> grids, List<Line> paragraph) {
        Line caption = paragraph.get(0);
        float gap = MAX_GAP * caption.fontSize();
        float captionTop = caption.pageY() - caption.fontSize();
        float captionEnd = paragraph.get(paragraph.size() - 1).pageY();
        Grid above = null;
        Grid below = null;
        for (Grid grid : grids) {
            PageGraphics.Box box = grid.box();
            if (box.contains(caption.pageX(), caption.pageY())
                    || box.left() >= caption.pageX() + caption.width() || box.right() <= caption.pageX()) {
                continue;
            }
            if (box.bottom() <= caption.pageY() && captionTop - box.bottom() <= gap
                    && (above == null || box.bottom() > above.box().bottom())) {
                above = grid;
            } else if (box.top() >= captionEnd && box.top() - captionEnd <= gap
                    && (below == null || box.top() < below.box().top())) {
                below = grid;
            }
        }
        return above != null ? above : below;
    }

    private static boolean isHorizontal(PageGraphics.Box rule) {
        return rule.right() - rule.left() > rule.bottom() - rule.top();
    }

    /**
     * The lines of the page whose words are in the grid, in document order; {@code null} when the grid cannot
     * be a table (a line half in it, a tagged table or rotated text in it, a line already in another table).
     */
    private static List<Line> linesIn(PageGraphics.Box grid, List<Line> pageLines, Set<Line> taken) {
        List<Line> inGrid = new ArrayList<>();
        for (Line line : pageLines) {
            if (line.pageY() <= grid.top() || line.pageY() >= grid.bottom()) {
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
    private static List<List<String>> rows(List<Line> lines, Grid grid) {
        PageGraphics.Box box = grid.box();
        List<Float> bounds = new ArrayList<>(List.of(box.left()));
        for (float x : merged(grid.verticals().stream().map(rule -> (rule.left() + rule.right()) / 2).toList())) {
            if (x - box.left() > SAME && box.right() - x > SAME) {
                bounds.add(x);
            }
        }
        bounds.add(box.right());
        // the vertical rules on each column boundary, found once per table
        List<List<PageGraphics.Box>> ruledBounds = new ArrayList<>();
        for (int c = 0; c < bounds.size(); c++) {
            ruledBounds.add(new ArrayList<>());
        }
        for (PageGraphics.Box rule : grid.verticals()) {
            int c = Collections.binarySearch(bounds, (rule.left() + rule.right()) / 2);
            int nearest = c >= 0 ? c : nearest(bounds, -c - 1, (rule.left() + rule.right()) / 2);
            if (nearest > 0 && nearest + 1 < bounds.size()
                    && Math.abs(bounds.get(nearest) - (rule.left() + rule.right()) / 2) <= SAME) {
                ruledBounds.get(nearest).add(rule);
            }
        }
        // a rule narrower than half a column is no row border: LaTeX draws the underscore of "conv2_x" (arXiv
        // 1512.03385 Table 1) as a rule 1.9 pt wide, which cut each row of the table in two
        float narrowest = Float.MAX_VALUE;
        for (int c = 1; c < bounds.size(); c++) {
            narrowest = Math.min(narrowest, bounds.get(c) - bounds.get(c - 1));
        }
        float minRule = narrowest / 2;
        List<Float> bands = merged(grid.horizontals().stream().filter(rule -> rule.right() - rule.left() >= minRule)
                .map(rule -> (rule.top() + rule.bottom()) / 2).toList());

        List<TextLine> textLines = textLines(lines);
        List<List<String>> rows = new ArrayList<>();
        int placed = 0;
        int next = 0;
        for (int b = 0; b + 1 < bands.size(); b++) {
            float top = bands.get(b);
            float bottom = bands.get(b + 1);
            // text lines are sorted by y: each band takes the next ones
            while (next < textLines.size() && textLines.get(next).y() <= top) {
                next++;
            }
            int end = next;
            while (end < textLines.size() && textLines.get(end).y() <= bottom) {
                end++;
            }
            List<TextLine> band = textLines.subList(next, end);
            next = end;
            if (band.isEmpty()) {
                continue;
            }
            placed += band.size();
            boolean labelOnEveryLine = band.stream().allMatch(line -> line.words().stream()
                    .anyMatch(word -> column(word, bounds) == 0));
            if (labelOnEveryLine) {
                for (TextLine line : band) {
                    // within the band: a font size above the baseline can reach the rules of the row above
                    rows.add(cells(List.of(line), Math.max(top, line.y() - line.fontSize()), line.y(), bounds,
                            ruledBounds, rows.isEmpty()));
                }
            } else {
                rows.add(cells(band, top, bottom, bounds, ruledBounds, rows.isEmpty()));
            }
        }
        return placed == textLines.size() ? rows : null;
    }

    /** The index of the bound nearest to {@code x}, given its insertion point. */
    private static int nearest(List<Float> bounds, int insertion, float x) {
        if (insertion >= bounds.size()) {
            return bounds.size() - 1;
        }
        if (insertion == 0) {
            return 0;
        }
        return x - bounds.get(insertion - 1) <= bounds.get(insertion) - x ? insertion - 1 : insertion;
    }

    /** The cells of one row: words in reading order, merged cells to their first column. */
    private static List<String> cells(List<TextLine> lines, float top, float bottom, List<Float> bounds,
            List<List<PageGraphics.Box>> ruledBounds, boolean header) {
        int columns = bounds.size() - 1;
        boolean[] ruled = new boolean[columns];
        for (int c = 1; c < columns; c++) {
            ruled[c] = ruledBounds.get(c).stream()
                    .anyMatch(rule -> rule.top() < bottom - 0.5f && rule.bottom() > top + 0.5f);
        }
        List<List<Line.Word>> cells = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            cells.add(new ArrayList<>());
        }
        for (TextLine line : lines) {
            for (Line.Word word : line.words()) {
                int column = column(word, bounds);
                while (column > 0 && !ruled[column]) {
                    column--;
                }
                cells.get(column).add(word);
            }
        }
        return cells.stream().map(words -> ScientificPowers.cellText(words, header)).toList();
    }

    /** The column of the word's centre. */
    private static int column(Line.Word word, List<Float> bounds) {
        float centre = (word.left() + word.right()) / 2;
        int index = Collections.binarySearch(bounds, centre);
        int column = (index >= 0 ? index : -index - 2);
        return Math.max(0, Math.min(column, bounds.size() - 2));
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
            } else {
                textLines.add(new TextLine(line.pageY(), line.fontSize(), new ArrayList<>(line.words())));
            }
        }
        for (TextLine line : textLines) {
            line.words().sort(Comparator.comparingDouble(Line.Word::left));
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
