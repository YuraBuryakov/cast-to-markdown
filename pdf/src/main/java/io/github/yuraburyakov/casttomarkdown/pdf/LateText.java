package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Puts text drawn after the rest of its line back into the line. WeasyPrint (RFC 9562) draws the text of links
 * ({@code [RFC4122]}) and the numbers of list items after the paragraph, so PDFBox, which keeps the order of the
 * content stream, returns them as separate lines further down. Such a line goes into an earlier line of its page
 * on the same baseline when each of its words lies in a gap between that line's words, or just before its first
 * word or after its last one.
 *
 * <p>Sorting the whole page by position would do it too, but mixes the lines of two columns (Experiment 01).
 * Text far from the ends of the line (the value column of a title block, a page number in a table of contents,
 * the other column) stays where it is.
 * ponytail: tries only the last {@link #MAX_TRIES} lines on the same baseline.
 */
final class LateText {

    /**
     * On the same baseline, in pt. WeasyPrint puts the late text exactly on the baseline; labels of charts in
     * arXiv papers that are 0.3 pt or more off stay apart.
     */
    private static final float SAME_BASELINE = 0.1f;
    /** A list item number or bullet: text before the first word of a line is one, or touches that word. */
    private static final Pattern MARKER = Pattern.compile("[" + Lists.BULLETS + "]|\\d+[.)]");
    /** Text before the first or after the last word joins only within this share of the font size. */
    private static final float NEAR_END = 0.5f;
    /** A gap wider than this share of the font size between inserted text and its neighbours is a space. */
    private static final float SPACE = 0.1f;
    /** Words may overlap their neighbours by this much (pt), as rounded positions do. */
    private static final float SLACK = 0.5f;
    /** At most this many earlier lines on the same baseline are tried, newest first. */
    private static final int MAX_TRIES = 8;
    /** A line with more words takes no late text: real lines have far fewer, and joining is linear in them. */
    private static final int MAX_WORDS = 300;

    private LateText() {
    }

    static List<Line> insert(List<Line> lines) {
        List<Line> result = new ArrayList<>(lines.size());
        // indexes into result by baseline (tenths of a pt) of the current page, newest last
        Map<Integer, List<Integer>> byBaseline = new HashMap<>();
        int page = -1;
        for (Line line : lines) {
            if (line.page() != page) {
                page = line.page();
                byBaseline.clear();
            }
            if (!merged(result, byBaseline, line)) {
                byBaseline.computeIfAbsent(baselineKey(line), k -> new ArrayList<>()).add(result.size());
                result.add(line);
            }
        }
        return result;
    }

    private static int baselineKey(Line line) {
        return Math.round(line.y() * 10);
    }

    /** Whether the line went into an earlier line of its page in {@code result}. */
    private static boolean merged(List<Line> result, Map<Integer, List<Integer>> byBaseline, Line late) {
        if (!eligible(late) || Lists.isMarkerOnly(late)) {
            return false;
        }
        List<Integer> candidates = new ArrayList<>();
        int key = baselineKey(late);
        for (int k = key - 1; k <= key + 1; k++) {
            List<Integer> bucket = byBaseline.getOrDefault(k, List.of());
            // only the newest of each bucket: copying a whole bucket would make the search quadratic again
            candidates.addAll(bucket.subList(Math.max(0, bucket.size() - MAX_TRIES), bucket.size()));
        }
        candidates.sort(Comparator.reverseOrder());
        // a damaged or hostile page with thousands of pieces on one baseline must not take quadratic time
        for (int i : candidates.subList(0, Math.min(candidates.size(), MAX_TRIES))) {
            Line line = result.get(i);
            // MUST and MAY of RFC 9562 are 1 pt smaller; text of another size only fills a gap between words,
            // as pieces of formulas in arXiv papers on the same baseline would otherwise join at the line ends
            boolean sameSize = line.sizeKey() == late.sizeKey();
            if (eligible(line) && line.words().size() <= MAX_WORDS
                    && (sameSize || Math.abs(line.fontSize() - late.fontSize()) <= 1)
                    && Math.abs(line.y() - late.y()) < SAME_BASELINE) {
                Line joined = join(line, late, sameSize);
                if (joined != null) {
                    result.set(i, joined);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean eligible(Line line) {
        return !line.isTable() && !line.rotated() && !line.words().isEmpty();
    }

    /**
     * The line with the late words in their places, or {@code null} when they do not fit; text of another size
     * ({@code sameSize} false) only goes between two words of the line, a list number before them.
     */
    private static Line join(Line line, Line late, boolean sameSize) {
        List<Line.Word> words = line.words();
        int n = words.size();
        int[] slots = new int[late.words().size()];
        for (int w = 0; w < slots.length; w++) {
            slots[w] = slot(words, late.words().get(w));
            // a list number may come with it ("1. OPTIONAL"): it still goes before the first word
            if (slots[w] < 0 || !sameSize && (slots[w] == n
                    || slots[w] == 0 && !MARKER.matcher(late.words().get(w).text()).matches())) {
                return null;
            }
        }
        boolean oneSlot = allEqual(slots);
        if (!oneSlot && !late.text().strip().replaceAll("\\s+", " ").equals(
                late.words().stream().map(Line.Word::text).collect(Collectors.joining(" ")))) {
            return null;
        }
        // where each word of the line is in its text
        String text = line.text();
        int[] start = new int[n];
        int[] end = new int[n];
        int from = 0;
        for (int k = 0; k < n; k++) {
            start[k] = text.indexOf(words.get(k).text(), from);
            if (start[k] < 0) {
                return null;
            }
            end[k] = start[k] + words.get(k).text().length();
            from = end[k];
        }
        // insert from the last slot backwards, so that the positions found above stay valid
        StringBuilder out = new StringBuilder(text);
        int w = slots.length;
        while (w > 0) {
            int slot = slots[w - 1];
            int first = w - 1;
            while (first > 0 && slots[first - 1] == slot) {
                first--;
            }
            List<Line.Word> piece = late.words().subList(first, w);
            String pieceText = oneSlot ? late.text().strip()
                    : piece.stream().map(Line.Word::text).collect(Collectors.joining(" "));
            int cutFrom = slot == 0 ? 0 : end[slot - 1];
            int cutTo = slot == n ? text.length() : start[slot];
            if (!text.substring(cutFrom, cutTo).isBlank()) {
                return null;
            }
            if (!nearEnd(slot == 0 ? piece : null, slot == n ? piece : null, words, late.fontSize())) {
                return null;
            }
            // before the first word only a list number ("5. [RFC4122] did") or text touching it ("[RFC4122]. Both"):
            // a row label of a table 3 pt left of the row stays apart
            if (slot == 0 && !MARKER.matcher(piece.get(0).text()).matches()
                    && words.get(0).left() - piece.get(piece.size() - 1).right() > SPACE * late.fontSize()) {
                return null;
            }
            String before = slot == 0 ? "" : space(words.get(slot - 1).right(), piece.get(0).left(), late.fontSize());
            String after = slot == n ? "" : space(piece.get(piece.size() - 1).right(), words.get(slot).left(), late.fontSize());
            out.replace(cutFrom, cutTo, before + pieceText + after);
            w = first;
        }
        List<Line.Word> allWords = Stream.concat(words.stream(), late.words().stream())
                .sorted(Comparator.comparingDouble(Line.Word::left)).toList();
        boolean startsEarlier = slots[0] == 0;
        float x = startsEarlier ? late.x() : line.x();
        float right = Math.max(line.x() + line.width(), late.x() + late.width());
        return new Line(line.page(), line.pageHeight(), x, line.y(), line.fontSize(), line.bold(), false,
                out.toString(), -1, right - x,
                startsEarlier ? late.pageX() : line.pageX(), startsEarlier ? late.pageY() : line.pageY(), allWords);
    }

    private static boolean allEqual(int[] slots) {
        for (int slot : slots) {
            if (slot != slots[0]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Index of the word of the line the late word goes before ({@code words.size()} for after the last one),
     * or {@code -1} when it overlaps a word.
     */
    private static int slot(List<Line.Word> words, Line.Word late) {
        int n = words.size();
        if (late.right() <= words.get(0).left() + SLACK) {
            return 0;
        }
        if (late.left() >= words.get(n - 1).right() - SLACK) {
            return n;
        }
        for (int k = 1; k < n; k++) {
            if (late.left() >= words.get(k - 1).right() - SLACK && late.right() <= words.get(k).left() + SLACK) {
                return k;
            }
        }
        return -1;
    }

    /**
     * Whether the words before the first word of the line ({@code before}) or after its last one ({@code after})
     * follow each other and the line without a gap wider than {@link #NEAR_END}; {@code null} for neither.
     */
    private static boolean nearEnd(List<Line.Word> before, List<Line.Word> after, List<Line.Word> words, float fontSize) {
        List<Line.Word> chain = new ArrayList<>();
        if (before != null) {
            chain.addAll(before);
            chain.add(words.get(0));
        } else if (after != null) {
            chain.add(words.get(words.size() - 1));
            chain.addAll(after);
        }
        for (int i = 1; i < chain.size(); i++) {
            if (chain.get(i).left() - chain.get(i - 1).right() > NEAR_END * fontSize) {
                return false;
            }
        }
        return true;
    }

    private static String space(float left, float right, float fontSize) {
        return right - left > SPACE * fontSize ? " " : "";
    }
}
