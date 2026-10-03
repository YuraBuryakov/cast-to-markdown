package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words split by a hyphen at the end of a line ("learn-" / "ing"). Justified text (LaTeX, Word) breaks
 * words, and Markdown would show "learn- ing". The document itself decides, nothing is guessed:
 * <ul>
 *   <li>the word without the hyphen is elsewhere in the document: join it ("learning");</li>
 *   <li>the word with the hyphen is elsewhere: a compound, keep the hyphen ("multi-layer");</li>
 *   <li>neither: leave both lines as they are ("high-" / "level" may be either).</li>
 * </ul>
 * The rest of the word moves up to the line with the hyphen.
 */
final class Hyphens {

    /** A letter word before the hyphen at the end of the line. */
    private static final Pattern LINE_END = Pattern.compile("(\\p{L}+)-$");
    /** A lower-case word at the start of the next line, the punctuation stuck to it, and the rest. */
    private static final Pattern LINE_START = Pattern.compile("^(\\p{Ll}\\p{L}*)(\\S*)(.*)$");
    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:-\\p{L}+)*");

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

    /** The lines of one paragraph, with split words joined where the document shows how. */
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
            String line = result.get(i);
            if (words.contains((head + tail).toLowerCase(Locale.ROOT))) {
                line = line.substring(0, line.length() - 1) + tail + start.group(2);
            } else if (words.contains((head + "-" + tail).toLowerCase(Locale.ROOT))) {
                line = line + tail + start.group(2);
            } else {
                continue;
            }
            result.set(i, line);
            String rest = start.group(3).strip();
            if (rest.isEmpty()) {
                result.remove(i + 1);
                i--; // the next line may end with a hyphen too
            } else {
                result.set(i + 1, rest);
            }
        }
        return result;
    }
}
