package io.github.yuraburyakov.casttomarkdown;

import io.github.yuraburyakov.casttomarkdown.internal.ConvertedDocument;
import java.util.Optional;

/**
 * Result of a conversion.
 *
 * <p>Immutable. Instances are created only by {@link CastToMarkdown}. New accessors
 * (warnings) may be added in future versions without breaking existing callers.
 *
 * <p>The metadata ({@link #title()}, {@link #author()}, {@link #language()}) is what the document states about
 * itself, taken as it is: a PDF made by Word may give "Microsoft Word - report.docx" as its title. It is empty when
 * the document states nothing or the format has no such field (TXT, CSV).
 */
public final class PreparedDocument {

    private final ConvertedDocument document;

    PreparedDocument(ConvertedDocument document) {
        this.document = document;
    }

    /**
     * Returns the document as Markdown. Never {@code null}; empty if the document has no text.
     * Lines are separated by {@code \n}; a non-empty result ends with a single {@code \n}.
     *
     * @return the Markdown text
     */
    public String markdown() {
        return document.markdown();
    }

    /**
     * The title the document gives itself: the title of the PDF document information, of the DOCX document
     * properties, the {@code <title>} of an HTML page. On one line, without spaces around it.
     *
     * @return the title, or empty
     */
    public Optional<String> title() {
        return Optional.ofNullable(document.title());
    }

    /**
     * The author the document names: the author of the PDF document information, the creator of the DOCX
     * document properties, {@code <meta name="author">} of an HTML page.
     *
     * @return the author, or empty
     */
    public Optional<String> author() {
        return Optional.ofNullable(document.author());
    }

    /**
     * The language of the document as it states it, usually a BCP 47 tag such as {@code en} or {@code de-DE}:
     * {@code /Lang} of a PDF, the language of the DOCX document properties or of its default text style,
     * {@code <html lang>} of an HTML page. Not checked and not guessed from the text.
     *
     * @return the language, or empty
     */
    public Optional<String> language() {
        return Optional.ofNullable(document.language());
    }

    @Override
    public String toString() {
        return "PreparedDocument[markdown=" + markdown().length() + " chars]";
    }
}
