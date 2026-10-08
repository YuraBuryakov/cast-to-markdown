package io.github.yuraburyakov.casttomarkdown.pdf;

import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words split by a hyphen at the end of a line ("learn-" / "ing"). Justified text (LaTeX, Word) breaks
 * words, and Markdown would show "learn- ing". The rest of the word always moves up to the line with the
 * hyphen; whether the hyphen goes:
 * <ul>
 *   <li>the word with the hyphen is elsewhere in the document: a compound, keep it ("multi-layer");</li>
 *   <li>otherwise the word without the hyphen is elsewhere, or the rest is an ending that is no word
 *       ({@link #ENDING}: "surpris-" / "ing"): join it ("learning");</li>
 *   <li>otherwise keep it ("high-level"): a kept hyphen of a split word ("sur-prisingly") reads better than
 *       a lost one of a compound. On 270 labelled breaks of the sample corpus no compound lost its hyphen
 *       (Q-PDF-HYPH).</li>
 * </ul>
 */
final class Hyphens {

    /** A letter word before the hyphen at the end of the line. */
    private static final Pattern LINE_END = Pattern.compile("(\\p{L}+)-\\s*$");
    /** A lower-case word at the start of the next line, the punctuation stuck to it, and the rest. */
    private static final Pattern LINE_START = Pattern.compile("^(\\p{Ll}\\p{L}*)(\\S*)(.*)$");
    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:-\\p{L}+)*");
    /** Endings a hyphenation breaks off a word and no compound ends with. */
    private static final Pattern ENDING = Pattern.compile("ing|ings|tions?|sions?|ity|ities|ments?|ness|able|ible|ics");

    private Hyphens() {
    }

    /** Lower-case words of the lines, with their inner hyphens. */
    static Set<String> words(List<Line> lines) {
        Set<String> words = new HashSet<>();
        for (Line line : lines) {
            Matcher word = WORD.matcher(line.text());
            while (word.find()) {
                words.add(word.group().toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }

    /** The Markdown lines of one paragraph, with split words joined. */
    static List<String> join(List<String> lines, Set<String> words) {
        List<String> result = new ArrayList<>(lines);
        for (int i = 0; i + 1 < result.size(); i++) {
            Matcher end = LINE_END.matcher(result.get(i));
            Matcher start = LINE_START.matcher(result.get(i + 1).stripLeading());
            if (!end.find() || !start.matches()) {
                continue;
            }
            String head = end.group(1);
            String tail = start.group(1);
            String line = result.get(i).stripTrailing();
            boolean hyphenated = words.contains((head + "-" + tail).toLowerCase(Locale.ROOT));
            boolean joined = !hyphenated && (words.contains((head + tail).toLowerCase(Locale.ROOT))
                    || ENDING.matcher(tail).matches());
            line = joined ? line.substring(0, line.length() - 1) + tail + start.group(2)
                    : line + tail + start.group(2);
            result.set(i, line);
            String rest = start.group(3).strip();
            if (rest.isEmpty()) {
                result.remove(i + 1);
                i--; // the next line may end with a hyphen too
            } else {
                result.set(i + 1, Markdown.escape(rest));
            }
        }
        return result;
    }
}
