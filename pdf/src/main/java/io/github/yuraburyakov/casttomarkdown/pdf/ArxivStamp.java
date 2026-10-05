package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.List;
import java.util.regex.Pattern;

/** A complete left-margin stamp, reconstructed only at serialization time. */
final class ArxivStamp {
    private static final Pattern STAMP = Pattern.compile("arXiv:[0-9]{2}(?:0[1-9]|1[0-2])\\.[0-9]{4,5}"
            + "(?:v[1-9][0-9]*)? \\[[A-Za-z][A-Za-z0-9.-]*\\] "
            + "(?:[1-9]|[12][0-9]|3[01]) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-9]{4}");

    record Block(String text, int end) { }

    private ArxivStamp() { }

    static Block at(List<List<Line>> paragraphs, int[] levels, int start, float left) {
        Line first = singleton(paragraphs.get(start));
        if (first == null || first.page() != 1 || !first.rotated() || first.isTable()
                || !Float.isFinite(left) || first.fontSize() <= 0
                || first.pageX() > left - first.fontSize() * 0.5f) {
            return null;
        }
        if (start > 0 && inBand(singleton(paragraphs.get(start - 1)), first)) {
            return null; // Never match a suffix of a rejected maximal chain.
        }
        StringBuilder raw = new StringBuilder();
        Line previous = null;
        boolean valid = true;
        int end = start;
        while (end < paragraphs.size()) {
            Line line = singleton(paragraphs.get(end));
            if (!inBand(line, first)) {
                break;
            }
            valid &= levels[end] == 0 && Float.isFinite(line.width()) && line.width() > 0
                    && Float.isFinite(line.x()) && Float.isFinite(line.pageY())
                    && Math.abs(line.y() - first.y()) <= first.fontSize() * 0.01f;
            if (previous != null) {
                valid &= line.x() > previous.x() && line.pageY() < previous.pageY()
                        && Math.abs(line.x() - previous.x() - previous.width()) <= first.fontSize() * 0.05f;
            }
            raw.append(line.text()); // Spaces inside fragments are significant.
            previous = line;
            end++;
        }
        String text = raw.toString().replaceAll("\\s+", " ").strip();
        return valid && end - start >= 2 && STAMP.matcher(text).matches() ? new Block(text, end) : null;
    }

    private static Line singleton(List<Line> paragraph) {
        return paragraph.size() == 1 ? paragraph.get(0) : null;
    }

    private static boolean inBand(Line line, Line first) {
        return line != null && line.page() == 1 && line.rotated() && !line.isTable()
                && line.sizeKey() == first.sizeKey()
                && Math.abs(line.pageX() - first.pageX()) <= first.fontSize() * 0.01f;
    }

    /** A margin needs multiple substantial horizontal lines of the dominant text size. */
    static float leftMargin(List<Line> lines) {
        var weights = new java.util.HashMap<Integer, Integer>();
        for (Line line : lines) {
            if (anchor(line)) {
                weights.merge(line.sizeKey(), line.text().length(), Integer::sum);
            }
        }
        int size = weights.entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(-1);
        List<Line> body = lines.stream().filter(l -> anchor(l) && l.sizeKey() == size).toList();
        return body.size() < 3 ? Float.NaN : (float) body.stream().mapToDouble(Line::pageX).min().orElse(Double.NaN);
    }

    private static boolean anchor(Line line) {
        return line.page() == 1 && !line.rotated() && !line.isTable() && line.text().strip().length() >= 40
                && Float.isFinite(line.pageX()) && line.fontSize() > 0;
    }
}
