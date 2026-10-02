package io.github.yuraburyakov.casttomarkdown.internal;

import java.util.regex.Pattern;

/** Markdown text helpers shared by the format modules. */
public final class Markdown {

    private static final Pattern LEADING_HASH = Pattern.compile("^(\\s*)#");

    private Markdown() {
    }

    /**
     * Escapes {@code #} at the start of a line, which Markdown reads as a heading.
     * ponytail: other block markers ({@code >}, {@code -}, {@code *}, code fences) are not escaped yet (Q-API-02).
     */
    public static String escape(String line) {
        return LEADING_HASH.matcher(line).replaceFirst("$1\\\\#");
    }

    /**
     * Unifies line endings, removes trailing spaces, collapses runs of blank lines into one
     * and makes a non-empty result end with a single {@code \n}.
     */
    public static String normalize(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        StringBuilder out = new StringBuilder(text.length());
        boolean blankLineBefore = false;
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (trimmed.isEmpty()) {
                blankLineBefore = out.length() > 0;
                continue;
            }
            if (out.length() > 0) {
                out.append(blankLineBefore ? "\n\n" : "\n");
            }
            out.append(trimmed);
            blankLineBefore = false;
        }
        return out.length() == 0 ? "" : out.append('\n').toString();
    }
}
