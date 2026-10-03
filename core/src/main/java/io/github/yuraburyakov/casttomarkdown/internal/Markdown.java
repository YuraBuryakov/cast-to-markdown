package io.github.yuraburyakov.casttomarkdown.internal;

import java.util.regex.Pattern;

/** Markdown text helpers shared by the format modules. */
public final class Markdown {

    private static final Pattern LEADING_HASH = Pattern.compile("^(\\s*)#");
    private static final Pattern SAFE_URL = Pattern.compile("(?i)(https?|mailto):");
    /** A link target with these characters is written as {@code <url>}, which CommonMark reads as one target. */
    private static final Pattern NEEDS_ANGLE_BRACKETS = Pattern.compile("[\\s()<>]");
    private static final Pattern TRAILING_SPACE = Pattern.compile("(\\s|%20)+$");
    private static final Pattern ADDRESS_LIKE = Pattern.compile("/|^www\\.|://");
    /** Sentence punctuation after an address in running text: "see https://example.org/x." */
    private static final Pattern ADDRESS_END_PUNCTUATION = Pattern.compile("[.,;:)]+$");

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
     * {@code [text](url)}; spaces around the text stay outside the brackets. Stays plain text when the
     * text is blank or is the address itself ({@code [url](url)} only repeats it), and for schemes other
     * than http, https and mailto: documents are untrusted, {@code javascript:} must not reach a renderer.
     */
    public static String link(String text, String url) {
        String label = text.strip();
        // a space typed after the address ends up in the target as %20
        String target = url == null ? "" : TRAILING_SPACE.matcher(url.strip()).replaceFirst("");
        if (label.isEmpty() || !SAFE_URL.matcher(target).lookingAt() || label.equals(target)
                || ("mailto:" + label).equalsIgnoreCase(target) || isPartOfAddress(label, target)) {
            return text;
        }
        if (NEEDS_ANGLE_BRACKETS.matcher(target).find()) {
            target = "<" + target.replace("<", "%3C").replace(">", "%3E") + ">";
        }
        int start = text.indexOf(label);
        return text.substring(0, start) + "[" + label.replace("[", "\\[").replace("]", "\\]") + "](" + target + ")"
                + text.substring(start + label.length());
    }

    /**
     * Whether the text is the address, or a piece of it, written out: a PDF splits a long address over lines,
     * and {@code [https://](https://github.com/x)} on one line and {@code [github.com/x](...)} on the next only
     * repeat it. Only text that looks like an address: with {@code /}, {@code www.} or {@code ://}, or the
     * dotted end of the address ({@code NIST.FIPS.202.pdf}). A link named "Btrfs" or "128-bit" to
     * {@code .../wiki/Btrfs} stays a link.
     */
    private static boolean isPartOfAddress(String label, String url) {
        String piece = ADDRESS_END_PUNCTUATION.matcher(label).replaceFirst("");
        if (piece.isEmpty() || piece.contains(" ")) {
            return false;
        }
        return ADDRESS_LIKE.matcher(piece).find() && url.contains(piece) || piece.contains(".") && url.endsWith(piece);
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
