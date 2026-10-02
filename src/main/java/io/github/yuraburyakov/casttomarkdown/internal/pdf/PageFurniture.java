package io.github.yuraburyakov.casttomarkdown.internal.pdf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes running headers, footers, page numbers and repeated text in page margins.
 *
 * <p>A line is removed only when both hold: it is at the top or bottom edge of the page (or its text
 * is rotated, like vertical text in a side margin), and the same text, with numbers ignored, is at
 * the edge of many pages. Repetition alone is not enough: tables repeat numbers on every page.
 */
final class PageFurniture {

    /** Top and bottom part of the page, as a share of the page height, where headers and footers are. */
    private static final float EDGE = 0.15f;
    /** Minimum share of pages with text on which the same edge line must appear. */
    private static final float MIN_PAGE_SHARE = 0.4f;
    private static final int MIN_PAGES = 3;
    private static final float X_BUCKET = 20;
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern WORD = Pattern.compile("\\p{L}{3}");
    /** A line that is only a roman page number: "iv", "xii". */
    private static final Pattern ROMAN_NUMBER = Pattern.compile("^[ivxlcdm]+$");

    private PageFurniture() {
    }

    static List<Line> remove(List<Line> lines) {
        Map<String, Set<Integer>> pagesByKey = new HashMap<>();
        Set<Integer> pages = new HashSet<>();
        for (Line line : lines) {
            pages.add(line.page());
            if (atEdge(line)) {
                pagesByKey.computeIfAbsent(key(line), k -> new HashSet<>()).add(line.page());
            }
        }
        int minPages = Math.max(MIN_PAGES, (int) Math.ceil(MIN_PAGE_SHARE * pages.size()));
        return lines.stream()
                .filter(line -> !atEdge(line) || pagesByKey.get(key(line)).size() < minPages)
                .toList();
    }

    /**
     * Rotated text counts only with a word in it: page numbers are never rotated, but rotated
     * axis labels of charts are numbers that repeat on many pages.
     */
    private static boolean atEdge(Line line) {
        if (line.rotated()) {
            return WORD.matcher(line.text()).find();
        }
        return line.y() < EDGE * line.pageHeight()
                || line.y() > (1 - EDGE) * line.pageHeight();
    }

    /**
     * Text with numbers replaced by {@code 0}, so "Page 3" and "Page 4", "iv" and "12" match, plus the
     * horizontal position: a page number stays in one place, chart labels with numbers do not.
     * ponytail: positions are rounded to 20 pt, so a header that moves across a bucket border is missed.
     */
    private static String key(Line line) {
        String text = NUMBER.matcher(line.text().strip().toLowerCase(Locale.ROOT)).replaceAll("0");
        return (ROMAN_NUMBER.matcher(text).matches() ? "0" : text) + "@" + Math.round(line.x() / X_BUCKET);
    }
}
