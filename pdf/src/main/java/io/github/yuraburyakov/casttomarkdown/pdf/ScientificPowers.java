package io.github.yuraburyakov.casttomarkdown.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Conservative Unicode powers in M×10^E; no general formula or footnote recognition. */
final class ScientificPowers {
    private static final Pattern NUMBER_TIMES = Pattern.compile("[0-9]+(?:\\.[0-9]+)?×");
    private static final Pattern POWER = Pattern.compile("(?:((?:[0-9]+(?:\\.[0-9]+)?)?×))?10([0-9]{1,3})");
    private static final String DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    // Hypotheses around six ResNet measurements, not calibrated across PDF generators.
    private static final float MIN_SIZE = 0.55f;
    private static final float MAX_SIZE = 0.80f;
    private static final float MIN_RISE = 0.20f;
    private static final float MAX_RISE = 0.50f;
    private static final float MAX_SCRIPT_GAP = 0.15f;
    private static final float MAX_WORD_GAP = 0.25f;
    private static final float BASE_TOLERANCE = 0.05f;

    record Glyph(char text, float left, float right, float baseline, float size) {
    }

    /** precedingWord=-1 means the whole scientific expression is inside one Word. */
    record Power(String text, int precedingWord, boolean tableScale) {
    }

    private ScientificPowers() {
    }

    static List<Line.Word> detect(List<Line.Word> words, List<List<Glyph>> glyphs) {
        List<Line.Word> result = new ArrayList<>(words);
        for (int w = 0; w < words.size(); w++) {
            Line.Word word = words.get(w);
            List<Glyph> chars = glyphs.get(w);
            Matcher match = POWER.matcher(word.text());
            if (!match.matches() || chars.size() != word.text().length()) {
                continue;
            }
            StringBuilder raw = new StringBuilder();
            chars.forEach(g -> raw.append(g.text()));
            if (!raw.toString().equals(word.text())) {
                continue; // Ligatures/normalisation do not give reliable character offsets.
            }
            int baseStart = match.group(1) == null ? 0 : match.group(1).length();
            Glyph base = chars.get(baseStart);
            if (base.size() <= 0 || !sameBase(base, chars.get(baseStart + 1))) {
                continue;
            }
            int preceding = -1;
            if (match.group(1) == null) {
                preceding = neighbour(words, glyphs, w, base);
                if (preceding < 0) {
                    continue;
                }
            } else if (chars.subList(0, baseStart).stream().anyMatch(g -> !sameBase(base, g))) {
                continue;
            }
            int exponent = baseStart + 2;
            Glyph previous = chars.get(exponent - 1);
            float scriptSize = chars.get(exponent).size();
            boolean valid = true;
            for (int i = exponent; i < chars.size(); i++) {
                Glyph g = chars.get(i);
                float ratio = g.size() / base.size();
                float rise = (base.baseline() - g.baseline()) / base.size();
                float gap = (g.left() - previous.right()) / base.size();
                if (ratio < MIN_SIZE || ratio > MAX_SIZE || rise < MIN_RISE || rise > MAX_RISE
                        || gap < -0.01f || gap > MAX_SCRIPT_GAP || Math.abs(g.size() - scriptSize) > 0.01f) {
                    valid = false;
                    break;
                }
                previous = g;
            }
            if (valid) {
                StringBuilder powered = new StringBuilder(word.text());
                for (int i = exponent; i < powered.length(); i++) {
                    powered.setCharAt(i, DIGITS.charAt(powered.charAt(i) - '0'));
                }
                result.set(w, new Line.Word(word.text(), word.left(), word.right(), new Power(powered.toString(), preceding, "×".equals(match.group(1)))));
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameBase(Glyph base, Glyph other) {
        return Math.abs(base.baseline() - other.baseline()) <= BASE_TOLERANCE * base.size()
                && Math.abs(base.size() - other.size()) <= 0.01f * base.size();
    }

    private static int neighbour(List<Line.Word> words, List<List<Glyph>> glyphs, int current, Glyph base) {
        int closest = -1;
        for (int i = 0; i < words.size(); i++) {
            if (i != current && words.get(i).right() <= base.left()
                    && (closest < 0 || words.get(i).right() > words.get(closest).right())) {
                closest = i;
            }
        }
        if (closest < 0 || !NUMBER_TIMES.matcher(words.get(closest).text()).matches()) {
            return -1;
        }
        List<Glyph> before = glyphs.get(closest);
        if (before.isEmpty() || before.stream().anyMatch(g -> !sameBase(base, g))) {
            return -1;
        }
        float edge = words.get(closest).right();
        if (base.left() - edge > MAX_WORD_GAP * base.size()) {
            return -1;
        }
        for (int i = 0; i < glyphs.size(); i++) {
            if (i != closest && i != current && glyphs.get(i).stream()
                    .anyMatch(g -> g.right() > edge && g.left() < base.left())) {
                return -1;
            }
        }
        return closest;
    }

    /** Ruled table cells never use context from another Word/column. */
    static String cellText(Line.Word word) {
        return word.power() != null && word.power().precedingWord() < 0 && !word.power().tableScale() ? word.power().text() : word.text();
    }

    /** Standalone scales are only emitted at the end of a recognised params header cell. */
    static String cellText(List<Line.Word> words, boolean header) {
        boolean params = header && words.stream().anyMatch(w -> w.text().equalsIgnoreCase("params"));
        List<String> text = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            Line.Word word = words.get(i);
            Power power = word.power();
            text.add(params && i == words.size() - 1 && power != null && power.tableScale()
                    ? power.text() : cellText(word));
        }
        return String.join(" ", text);
    }

    /** Run after RuledTables: its row lines have gone, so only ordinary text can use a neighbour. */
    static List<Line> apply(PDDocument document, List<Line> lines) throws IOException {
        Map<Integer, List<PageGraphics.Box>> graphics = new HashMap<>();
        List<Line> result = new ArrayList<>(lines.size());
        for (Line line : lines) {
            boolean crossWord = line.words().stream().anyMatch(w -> w.power() != null && w.power().precedingWord() >= 0);
            List<PageGraphics.Box> rules = List.of();
            if (crossWord && !graphics.containsKey(line.page())) {
                graphics.put(line.page(), PageGraphics.of(document.getPage(line.page() - 1)));
            }
            if (crossWord) {
                rules = graphics.get(line.page());
            }
            result.add(apply(line, rules));
        }
        return result;
    }

    static Line apply(Line line, List<PageGraphics.Box> rules) {
        if (line.isTable() || line.rotated() || line.words().stream().noneMatch(w -> w.power() != null)) {
            return line;
        }
        StringBuilder text = new StringBuilder(line.text());
        List<Line.Word> words = new ArrayList<>();
        int offset = 0;
        for (Line.Word word : line.words()) {
            while (offset < line.text().length() && Character.isWhitespace(line.text().charAt(offset))) {
                offset++;
            }
            if (!line.text().startsWith(word.text(), offset)) {
                return line; // No guessing offsets in links/normalised/mixed strings.
            }
            String changed = word.text();
            Power power = word.power();
            if (power != null && !power.tableScale() && (power.precedingWord() < 0 || !blocked(line, word, power, rules))) {
                changed = power.text();
                text.replace(offset, offset + word.text().length(), changed);
            }
            words.add(new Line.Word(changed, word.left(), word.right()));
            offset += word.text().length();
        }
        if (text.toString().equals(line.text())) {
            return line;
        }
        return new Line(line.page(), line.pageHeight(), line.x(), line.y(), line.fontSize(), line.bold(),
                line.rotated(), text.toString(), line.table(), line.width(), line.pageX(), line.pageY(), List.copyOf(words));
    }

    private static boolean blocked(Line line, Line.Word word, Power power, List<PageGraphics.Box> rules) {
        if (rules.size() > PageGraphics.MAX_BOXES) {
            return true;
        }
        float left = line.words().get(power.precedingWord()).right();
        return rules.stream().anyMatch(b -> b.right() - b.left() <= 1.5f
                && b.bottom() - b.top() >= 0.5f * line.fontSize()
                && b.left() <= word.left() && b.right() >= left
                && b.top() <= line.pageY() && b.bottom() >= line.pageY());
    }
}
