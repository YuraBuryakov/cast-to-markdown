package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Bullet lists: puts list markers back on their lines and renders them as Markdown {@code - } items.
 * Numbered items ({@code 1.}, {@code 1)}) are already Markdown and are left as they are.
 * ponytail: nesting by indentation is not detected; every bullet item is a top-level item.
 */
final class Lists {

    /** Bullet characters used as list markers: bullet, white bullet, small squares, triangle, black circle. */
    static final String BULLETS = "\u2022\u25e6\u25aa\u2023\u25cf\u25a0";
    private static final Pattern MARKER_ONLY = Pattern.compile("^\\s*[" + BULLETS + "]\\s*$");
    private static final Pattern BULLET_ITEM = Pattern.compile("^(\\s*)[" + BULLETS + "]\\s*");
    /** A marker belongs to a text line at the same height, within this share of the font size. */
    private static final float SAME_LINE = 0.3f;

    private Lists() {
    }

    /**
     * Some generators (WeasyPrint) draw list markers after the text, so PDFBox returns the marker as a
     * separate line elsewhere in the page. Such a marker is moved to the start of the nearest text line
     * to its right at the same height; a marker without such a line is kept as it is.
     * ponytail: compares every marker with every line of its page; fine for normal pages.
     */
    static List<Line> attachMarkers(List<Line> lines) {
        List<Line> result = new ArrayList<>(lines);
        for (int m = 0; m < result.size(); m++) {
            Line marker = result.get(m);
            if (!isMarkerOnly(marker)) {
                continue;
            }
            int item = -1;
            for (int i = 0; i < result.size(); i++) {
                Line line = result.get(i);
                if (i != m
                        && line.page() == marker.page()
                        && line.x() > marker.x()
                        && Math.abs(line.y() - marker.y()) < SAME_LINE * marker.fontSize()
                        && !isMarkerOnly(line)
                        && !line.isTable()
                        && (item < 0 || line.x() < result.get(item).x())) {
                    item = i;
                }
            }
            if (item >= 0) {
                Line text = result.get(item);
                result.set(item, new Line(text.page(), text.pageHeight(), marker.x(), text.y(), text.fontSize(),
                        text.bold(), text.rotated(), marker.text().strip() + " " + text.text().strip(), -1,
                        text.x() + text.width() - marker.x(), marker.pageX(), marker.pageY(),
                        Stream.concat(marker.words().stream(), text.words().stream()).toList(), text.heading()));
                result.remove(m);
                m--;
            }
        }
        return result;
    }

    static boolean isMarkerOnly(Line line) {
        return MARKER_ONLY.matcher(line.text()).matches();
    }

    /** A line starting with a bullet becomes a Markdown list item: {@code "\u2022 Text"} gives {@code "- Text"}. */
    static String markdown(String line) {
        return BULLET_ITEM.matcher(line).replaceFirst("$1- ");
    }
}
