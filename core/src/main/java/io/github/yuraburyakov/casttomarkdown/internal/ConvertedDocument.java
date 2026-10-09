package io.github.yuraburyakov.casttomarkdown.internal;

import java.util.regex.Pattern;

/**
 * What a {@link DocumentConverter} returns: the Markdown and the metadata the document states about itself.
 * A metadata value is {@code null} when the document has none; blank values become {@code null}, and runs of
 * white space one space.
 *
 * @param markdown the normalized Markdown
 * @param title the title of the document, or {@code null}
 * @param author the author, or {@code null}
 * @param language the language as the document states it ({@code en}, {@code de-DE}), or {@code null}
 */
public record ConvertedDocument(String markdown, String title, String author, String language) {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Strips the metadata values; blank ones become {@code null}.
     *
     * @param markdown the normalized Markdown
     * @param title the title of the document, or {@code null}
     * @param author the author, or {@code null}
     * @param language the language, or {@code null}
     */
    public ConvertedDocument {
        title = clean(title);
        author = clean(author);
        language = clean(language);
    }

    /**
     * A document without metadata.
     *
     * @param markdown the normalized Markdown
     * @return the document
     */
    public static ConvertedDocument of(String markdown) {
        return new ConvertedDocument(markdown, null, null, null);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = SPACES.matcher(value).replaceAll(" ").strip();
        return text.isEmpty() ? null : text;
    }
}
