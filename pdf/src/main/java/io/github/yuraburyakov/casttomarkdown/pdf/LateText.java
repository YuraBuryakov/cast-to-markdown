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
    /** Lines between a line and its late text that a formula puts there, at most. */
    private static final int FEW_LINES = 2;
    /** A line ending with a word of letters split by a hyphen. */
    private static final Pattern SPLIT_WORD = Pattern.compile("(^|\\s)\\p{L}+-\\s*$");
    /** Text before the first or after the last word joins only within this share of the font size. */
    private static final float NEAR_END = 0.5f;
    /** A gap wider than this share of the font size between inserted text and its neighbours is a space. */
    private static final float SPACE = 0.1f;
    /** Words may overlap their neighbours by this much (pt), as rounded positions do. */
    private static final float SLACK = 0.5f;
    /** At most this many earlier lines on the same baseline are tried, newest first. */
    private static final int MAX_TRIES = 8;
    /** A whole late line goes back at most this many lines. */
    private static final int LOOK_BACK = 6;
    /** A reference label is at most this many font sizes left of its entry (see {@link #spliced}). */
    private static final float NEAR_PIECE = 1.5f;
    /** The label of a reference entry: {@code [RFC2119]}, {@code [C309]}. */
    private static final Pattern LABEL = Pattern.compile("\\[[^\\]\\s]+\\]");
    /** A late line above the text of its page starts at most this many font sizes right of its first line. */
    private static final float TOP_INDENT = 2;
    /** A late line goes to the top of its page only when the page so far has at most this many lines. */
    private static final int MAX_PAGE_LINES = 300;
    /** A line with more words takes no late text: real lines have far fewer, and joining is linear in them. */
    private static final int MAX_WORDS = 300;

    private LateText() {
    }

    static List<Line> insert(List<Line> lines) {
        List<Line> result = new ArrayList<>(lines.size());
        // indexes into result by baseline (tenths of a pt) of the current page, newest last
        Map<Integer, List<Integer>> byBaseline = new HashMap<>();
        int page = -1;
        for (int l = 0; l < lines.size(); l++) {
            Line line = lines.get(l);
            if (line.page() != page) {
                page = line.page();
                byBaseline.clear();
            }
            if (!merged(result, byBaseline, line, l + 1 < lines.size() ? lines.get(l + 1) : null)
                    && !spliced(result, byBaseline, line)) {
                byBaseline.computeIfAbsent(baselineKey(line), k -> new ArrayList<>()).add(result.size());
                result.add(line);
            }
        }
        result.removeIf(java.util.Objects::isNull);
        // labels after: a labelled line starts at its label, no longer at the left edge of the entries around it
        List<Line> inPlace = lateLinesInPlace(result);
        labelsToEntries(inPlace);
        inPlace.removeIf(java.util.Objects::isNull);
        return inPlace;
    }

    /**
     * A reference label alone on its line goes to the start of the entry on its baseline, wherever the entry is in
     * the list: RFC 9562 draws all labels of a page before its heading, and the first line of an entry is put together
     * from pieces only later. The label's place is left {@code null}.
     */
    private static void labelsToEntries(List<Line> result) {
        Map<Long, List<Integer>> byPageBaseline = new HashMap<>();
        for (int i = 0; i < result.size(); i++) {
            Line line = result.get(i);
            if (line != null) {
                byPageBaseline.computeIfAbsent((long) line.page() << 32 | baselineKey(line), k -> new ArrayList<>()).add(i);
            }
        }
        for (int i = 0; i < result.size(); i++) {
            Line label = result.get(i);
            if (label == null || !eligible(label) || label.words().size() != 1 || !LABEL.matcher(label.text().strip()).matches()) {
                continue;
            }
            List<Integer> same = byPageBaseline.get((long) label.page() << 32 | baselineKey(label));
            // ponytail: the first few lines on the baseline only, so that a hostile page stays linear
            for (int j : same.subList(0, Math.min(same.size(), MAX_TRIES))) {
                Line entry = result.get(j);
                if (j == i || entry == null || !eligible(entry) || entry.sizeKey() != label.sizeKey()
                        || entry.words().size() <= 1 || entry.x() <= label.x()) {
                    continue;
                }
                Line joined = splice(label, entry);
                if (joined != null) {
                    result.set(j, joined);
                    result.set(i, null);
                    break;
                }
            }
        }
    }

    /**
     * A line and an earlier piece on its baseline that the rules of {@link #merged} do not join, spliced into one line
     * by position, both in one font size: their words go between each other (WeasyPrint draws the commas and "and" of
     * an RFC 9562 reference entry with the entry before it, and the names after the page), or the earlier one is a
     * reference label at most {@link #NEAR_PIECE} font sizes left of the later one (the label {@code [C309]}, drawn
     * before the heading "9.1. Normative References", one font size left of its entry). The spliced line takes the place of the piece with more words, the other place is left {@code null}.
     * Two columns are further apart. Whether the line was spliced into {@code result}.
     */
    private static boolean spliced(List<Line> result, Map<Integer, List<Integer>> byBaseline, Line late) {
        if (!eligible(late) || late.words().size() > MAX_WORDS) {
            return false;
        }
        List<Integer> candidates = new ArrayList<>();
        int key = baselineKey(late);
        for (int k = key - 1; k <= key + 1; k++) {
            List<Integer> bucket = byBaseline.getOrDefault(k, List.of());
            candidates.addAll(bucket.subList(Math.max(0, bucket.size() - MAX_TRIES), bucket.size()));
        }
        candidates.sort(Comparator.reverseOrder());
        for (int i : candidates.subList(0, Math.min(candidates.size(), MAX_TRIES))) {
            Line line = result.get(i);
            if (line == null || !eligible(line) || line.words().size() > MAX_WORDS
                    || Math.abs(line.y() - late.y()) >= SAME_BASELINE || line.sizeKey() != late.sizeKey()) {
                continue;
            }
            Line joined = splice(line, late);
            if (joined != null) {
                if (line.words().size() >= late.words().size()) {
                    result.set(i, joined);
                } else {
                    result.set(i, null);
                    byBaseline.computeIfAbsent(key, k -> new ArrayList<>()).add(result.size());
                    result.add(joined);
                }
                return true;
            }
        }
        return false;
    }

    /** The two pieces as one line, or {@code null} when their words overlap or they are too far apart. */
    private static Line splice(Line a, Line b) {
        Line left = a.x() <= b.x() ? a : b;
        Line right = left == a ? b : a;
        float size = Math.max(a.fontSize(), b.fontSize());
        List<Line.Word> words = Stream.concat(a.words().stream(), b.words().stream())
                .sorted(Comparator.comparingDouble(Line.Word::left)).toList();
        for (int w = 1; w < words.size(); w++) {
            if (words.get(w).left() < words.get(w - 1).right() - SLACK) {
                return null;
            }
        }
        float leftEnd = left.words().get(left.words().size() - 1).right();
        String text;
        if (right.words().get(0).left() >= leftEnd - SLACK) {
            // side by side only for a reference label left of its entry; the texts stay as they are, with any links
            if (left.words().size() != 1 || !LABEL.matcher(left.text().strip()).matches()
                    || right.words().get(0).left() - leftEnd > NEAR_PIECE * size) {
                return null;
            }
            text = left.text().strip() + space(leftEnd, right.words().get(0).left(), size) + right.text().strip();
        } else {
            // between each other: only plain texts, rebuilt from the words
            if (!plain(a) || !plain(b)) {
                return null;
            }
            StringBuilder out = new StringBuilder(words.get(0).text());
            for (int w = 1; w < words.size(); w++) {
                out.append(space(words.get(w - 1).right(), words.get(w).left(), size)).append(words.get(w).text());
            }
            text = out.toString();
        }
        Line body = a.words().size() >= b.words().size() ? a : b;
        float rightEnd = Math.max(a.x() + a.width(), b.x() + b.width());
        return new Line(body.page(), body.pageHeight(), left.x(), body.y(), body.fontSize(), body.bold(), false, text, -1,
                rightEnd - left.x(), left.pageX(), body.pageY(), words, body.heading());
    }

    /** Whether the text of the line is its words with single spaces. */
    private static boolean plain(Line line) {
        return line.text().strip().replaceAll("\\s+", " ").equals(
                line.words().stream().map(Line.Word::text).collect(Collectors.joining(" ")));
    }

    /**
     * Whole lines drawn after the lines below them go back between them: WeasyPrint draws the title of a reference
     * (a link) after the rest of the entry, so "the operation of OSI Registration Authorities" (y 94.8) came after
     * "components", ISO/IEC 9834-8:2004" (y 122.0) in RFC 9562. Such a line goes between two lines that follow each
     * other, a few lines back on its page, when it lies between them, starts at the lower one's left edge and has its
     * font size or a larger one (a heading drawn late above its text, "3.2. Abbreviations" there). Done after the
     * baseline indexes above are used, as it moves lines.
     * ponytail: looks at most {@link #LOOK_BACK} lines back.
     */
    private static List<Line> lateLinesInPlace(List<Line> lines) {
        List<Line> result = new ArrayList<>(lines.size());
        int moved = -1; // index in result of the line moved last, while the lines after it may follow it
        for (Line line : lines) {
            int at = -1;
            if (moved >= 0 && moved + 1 < result.size() && followsMoved(result.get(moved), line, result.get(moved + 1))) {
                at = moved + 1;
            }
            for (int i = result.size() - 2; at < 0 && i >= 0 && i >= result.size() - 1 - LOOK_BACK; i--) {
                Line above = result.get(i);
                Line below = result.get(i + 1);
                if (above.page() == line.page() && below.page() == line.page() && eligible(line) && eligible(below)
                        && above.y() < line.y() && line.y() < below.y()
                        && Math.abs(line.x() - below.x()) < SLACK && line.sizeKey() >= below.sizeKey()) {
                    at = i + 1;
                }
            }
            if (at < 0) {
                at = topOfPage(result, line);
            }
            if (at < 0) {
                result.add(line);
                moved = -1;
            } else {
                result.add(at, line);
                moved = at;
            }
        }
        return result;
    }

    /** The next late line right below the one moved last, in its font: list items 10 to 16 after item 9. */
    private static boolean followsMoved(Line moved, Line line, Line below) {
        return eligible(line) && line.page() == moved.page() && below.page() == line.page()
                && moved.y() < line.y() && line.y() < below.y() && line.sizeKey() == moved.sizeKey()
                && line.y() - moved.y() < 2 * line.fontSize();
    }

    /**
     * Where a line drawn after the text of its page but above it goes: before the first line of the page below it,
     * when every earlier line of the page is well above it (a running header) and every later one below it, and it
     * starts at that line's left edge or up to {@link #TOP_INDENT} font sizes right of it (list items 9 to 16 of RFC
     * 9562 at the top of page 6, which WeasyPrint draws after the page). The left edge keeps the right column of a
     * two-column page, which also starts above the end of the left one, out. {@code -1} for nowhere.
     */
    private static int topOfPage(List<Line> result, Line line) {
        if (!eligible(line)) {
            return -1;
        }
        // a hostile page drawn bottom up would make every line walk back over the whole page
        int stop = Math.max(0, result.size() - MAX_PAGE_LINES);
        int at = result.size();
        while (at > stop && result.get(at - 1).page() == line.page() && result.get(at - 1).y() > line.y()) {
            at--;
        }
        if (at == result.size() || at == stop && stop > 0 && result.get(stop - 1).page() == line.page()) {
            return -1;
        }
        for (int i = at - 1; i >= 0 && result.get(i).page() == line.page(); i--) {
            if (i < stop || result.get(i).y() > line.y() - line.fontSize()) {
                return -1;
            }
        }
        Line first = result.get(at);
        return eligible(first) && line.sizeKey() >= first.sizeKey() && line.x() > first.x() - SLACK
                && line.x() - first.x() <= TOP_INDENT * line.fontSize() ? at : -1;
    }

    private static int baselineKey(Line line) {
        return Math.round(line.y() * 10);
    }

    /** Whether the line went into an earlier line of its page in {@code result}. */
    private static boolean merged(List<Line> result, Map<Integer, List<Integer>> byBaseline, Line late, Line next) {
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
            if (line == null) {
                continue; // spliced into a later line
            }
            // MUST and MAY of RFC 9562 are 1 pt smaller; text of another size only fills a gap between words,
            // as pieces of formulas in arXiv papers on the same baseline would otherwise join at the line ends
            boolean sameSize = line.sizeKey() == late.sizeKey();
            if (eligible(line) && line.words().size() <= MAX_WORDS
                    && (sameSize || Math.abs(line.fontSize() - late.fontSize()) <= 1)
                    && Math.abs(line.y() - late.y()) < SAME_BASELINE) {
                // a split word with its second half on the next line goes after the end only past many lines (a whole
                // reference entry of RFC 9562), not past a few (the denominator of a fraction in arXiv 1512.00567)
                boolean endAllowed = result.size() - 1 - i == 0 || result.size() - 1 - i > FEW_LINES
                        || !carriesOn(late, next);
                Line joined = join(line, late, sameSize, endAllowed);
                if (joined != null) {
                    result.set(i, joined);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the late line ends with a word split by a hyphen that the next line carries on ("abil-" / "ity").
     * Such a line does not go after the end of an earlier line with a few other lines between: the second half
     * of the word would stay behind them.
     */
    private static boolean carriesOn(Line late, Line next) {
        // a word of letters only: an address split at its hyphen ("<https://github.com/twitter-") still joins
        return next != null && SPLIT_WORD.matcher(late.text()).find() && !next.text().isBlank()
                && Character.isLowerCase(next.text().stripLeading().codePointAt(0));
    }

    private static boolean eligible(Line line) {
        return !line.isTable() && !line.rotated() && !line.words().isEmpty();
    }

    /**
     * The line with the late words in their places, or {@code null} when they do not fit; text of another size
     * ({@code sameSize} false) only goes between two words of the line, a list number before them.
     */
    private static Line join(Line line, Line late, boolean sameSize, boolean endAllowed) {
        List<Line.Word> words = line.words();
        int n = words.size();
        int[] slots = new int[late.words().size()];
        for (int w = 0; w < slots.length; w++) {
            slots[w] = slot(words, late.words().get(w));
            // a list number may come with it ("1. OPTIONAL"): it still goes before the first word
            if (slots[w] < 0 || slots[w] == n && !endAllowed || !sameSize && (slots[w] == n
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
                startsEarlier ? late.pageX() : line.pageX(), startsEarlier ? late.pageY() : line.pageY(), allWords,
                line.heading());
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
