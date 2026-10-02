package io.github.yuraburyakov.casttomarkdown;

/**
 * Result of a conversion.
 *
 * <p>Immutable. Instances are created only by {@link CastToMarkdown}. New accessors
 * (metadata, warnings) may be added in future versions without breaking existing callers.
 */
public final class PreparedDocument {

    private final String markdown;

    PreparedDocument(String markdown) {
        this.markdown = markdown;
    }

    /**
     * Returns the document as Markdown. Never {@code null}; empty if the document has no text.
     * Lines are separated by {@code \n}; a non-empty result ends with a single {@code \n}.
     */
    public String markdown() {
        return markdown;
    }

    @Override
    public String toString() {
        return "PreparedDocument[markdown=" + markdown.length() + " chars]";
    }
}
