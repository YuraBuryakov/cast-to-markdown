package io.github.yuraburyakov.casttomarkdown.internal;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Markdown text helpers shared by the format modules. */
public final class Markdown {

    private static final Pattern BLOCK_MARKER = Pattern.compile("^(\\s*)(#|>|```|~~~)");
    /**
     * A line that CommonMark reads as a rule, a heading underline or an empty list item: any run of
     * {@code -} or {@code =} ("-", "--", "==="), three or more {@code *} or {@code _} ("***", "_ _ _"),
     * or a single {@code *}. "**", "_" and "__" are plain text and stay as they are.
     */
    private static final Pattern RULE_LINE =
            Pattern.compile("^(\\s*)(?:([-=])(?:\\s*\\2)*|([*_])(?:\\s*\\3){2,}|\\*)\\s*$");
    private static final Pattern SAFE_URL = Pattern.compile("(?i)(https?|mailto):");
    /** A link target with these characters is written as {@code <url>}, which CommonMark reads as one target. */
    private static final Pattern NEEDS_ANGLE_BRACKETS = Pattern.compile("[\\s()<>]");
    private static final Pattern TRAILING_SPACE = Pattern.compile("(\\s|%20)+$");
    /** Line breaks inside link text: a blank line there would end the paragraph. */
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");
    /** The start of something CommonMark reads as raw HTML: a tag, a closing tag, a comment, an instruction. */
    /**
     * The start of tag-like text with the backslashes before it. Also an autolink: renderers differ on what is an
     * autolink and what a tag, so no exception is safe.
     */
    private static final Pattern TAG_START = Pattern.compile("(\\\\*)<(?=[A-Za-z/!?])");
    /** A pipe with the backslashes right before it (group 1). */
    private static final Pattern PIPE_WITH_BACKSLASHES = Pattern.compile("(\\\\*)\\|");
    /** What an autolink {@code <...>} cannot hold: CommonMark ends it there or does not read it at all. */
    private static final Pattern NOT_IN_AUTOLINK = Pattern.compile("[\\s\\p{Cntrl}<>]");
    /** The e-mail address CommonMark reads as {@code <team@example.org>}. */
    private static final Pattern AUTOLINK_EMAIL = Pattern.compile("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9]"
            + "(?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*");
    private static final Pattern ADDRESS_LIKE = Pattern.compile("/|^www\\.|://");
    /** Sentence punctuation after an address in running text: "see https://example.org/x." */
    private static final Pattern ADDRESS_END_PUNCTUATION = Pattern.compile("[.,;:)]+$");

    private Markdown() {
    }

    /**
     * Escapes what Markdown would read as block syntax at the start of a line of document text: a heading
     * ({@code #}), a quote ({@code >}), a code fence ({@code ```}, {@code ~~~}; an unclosed one turns the
     * rest of the document into code), and a line that is a horizontal rule, an empty list item or turns
     * the line above it into a heading ({@link #RULE_LINE}).
     * List markers ({@code -}, {@code *}, {@code 1.}) and inline syntax stay: PDFs write real lists as
     * text, and backslashes everywhere would only add noise (Q-API-02, option A).
     *
     * @param line one line of text
     * @return the line with its leading block syntax escaped
     */
    public static String escape(String line) {
        Matcher rule = RULE_LINE.matcher(line);
        if (rule.find()) {
            return line.substring(0, rule.end(1)) + "\\" + line.substring(rule.end(1));
        }
        return BLOCK_MARKER.matcher(line).replaceFirst("$1\\\\$2");
    }

    /**
     * {@code [text](url)}; spaces around the text stay outside the brackets. Text that is the address itself
     * becomes an autolink {@code <url>} ({@code [url](url)} only repeats it, and a bare address after a footnote
     * number, "1http://...", is no link to a GFM renderer). Stays plain text when the text is blank, is a piece
     * of the address, or an autolink cannot hold the address, and for schemes other than http, https and
     * mailto: documents are untrusted, {@code javascript:} must not reach a renderer.
     *
     * @param text the link text
     * @param url the link target; may be {@code null}
     * @return the Markdown link, or {@code text} unchanged
     */
    public static String link(String text, String url) {
        return link(text, url, false);
    }

    /**
     * Escapes text a CommonMark renderer would read as raw HTML and run or hide: {@code <} before a letter,
     * {@code /}, {@code !} or {@code ?} ({@code <script>}, {@code List<E>}, {@code <!--}). {@code a < b} and
     * {@code x<5} stay. An address in angle brackets ({@code <https://...>} of an RFC text file) is escaped too and
     * reads as text. Used by the text formats (TXT, CSV); PDF and DOCX do not escape yet (Q-API-02b).
     *
     * @param text text of the document
     * @return the text with each such {@code <} escaped
     */
    public static String escapeTags(String text) {
        // backslashes right before "<" are doubled: "\<b>" would read as an escaped backslash and a tag
        return TAG_START.matcher(text).replaceAll(match -> {
            String backslashes = match.group(1);
            return Matcher.quoteReplacement(backslashes + backslashes + "\\<");
        });
    }

    /**
     * Text of one table cell with every {@code |} escaped, and the backslashes right before it doubled: a GFM table
     * reads {@code \\} as an escaped backslash, so the {@code |} after {@code x\} would end the cell and shift the
     * columns of the row.
     *
     * @param text the text of the cell, on one line
     * @return the text safe inside a cell
     */
    public static String tableCell(String text) {
        return PIPE_WITH_BACKSLASHES.matcher(text).replaceAll(match -> {
            String backslashes = match.group(1);
            return Matcher.quoteReplacement(backslashes + backslashes + "\\|");
        });
    }

    /**
     * As {@link #link(String, String)}; with {@code wholeText} the text is the whole text of the link (HTML), so a
     * text that is in the address ({@code Lib/json/__init__.py}) is still a link.
     *
     * @param text the link text
     * @param url the link target; may be {@code null}
     * @param wholeText whether the text is all of the link's text, never a piece of an address split over lines
     * @return the Markdown link, or {@code text} unchanged
     */
    public static String link(String text, String url, boolean wholeText) {
        String label = text.strip();
        // a space typed after the address ends up in the target as %20
        String target = url == null ? "" : TRAILING_SPACE.matcher(url.strip()).replaceFirst("");
        if (label.isEmpty() || !SAFE_URL.matcher(target).lookingAt()) {
            return text;
        }
        if (label.equals(target) || ("mailto:" + label).equalsIgnoreCase(target)) {
            return autolink(text, label);
        }
        if (!wholeText && isPartOfAddress(label, target)) {
            return text;
        }
        target = encodeControlCharacters(target);
        if (NEEDS_ANGLE_BRACKETS.matcher(target).find()) {
            target = "<" + target.replace("<", "%3C").replace(">", "%3E") + ">";
        }
        int start = text.indexOf(label);
        String shown = LINE_BREAKS.matcher(label).replaceAll(" ").replace("[", "\\[").replace("]", "\\]");
        return text.substring(0, start) + "[" + shown + "](" + target + ")" + text.substring(start + label.length());
    }

    /** {@code <address>} in place of the address in the text, or the text unchanged if no autolink holds it. */
    private static String autolink(String text, String address) {
        boolean holds = !NOT_IN_AUTOLINK.matcher(address).find()
                && (SAFE_URL.matcher(address).lookingAt() || AUTOLINK_EMAIL.matcher(address).matches());
        if (!holds) {
            return text;
        }
        int start = text.indexOf(address);
        return text.substring(0, start) + "<" + address + ">" + text.substring(start + address.length());
    }

    /**
     * Percent-encodes control characters of a link target. A line break in an address taken from an
     * untrusted document ended the link, and "\n\n# heading" in it added a heading to the Markdown.
     */
    private static String encodeControlCharacters(String target) {
        StringBuilder encoded = new StringBuilder(target.length());
        for (char c : target.toCharArray()) {
            if (Character.isISOControl(c)) {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    encoded.append('%').append(String.format("%02X", b & 0xFF));
                }
            } else {
                encoded.append(c);
            }
        }
        return encoded.toString();
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
     *
     * @param text Markdown text
     * @return the normalized text
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
