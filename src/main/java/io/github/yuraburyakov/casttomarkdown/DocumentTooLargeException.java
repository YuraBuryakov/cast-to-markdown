package io.github.yuraburyakov.casttomarkdown;

/**
 * Thrown when a document is larger than the configured limit
 * ({@link CastToMarkdown.Builder#maxDocumentSize(long)}). The document is not converted.
 */
public class DocumentTooLargeException extends DocumentConversionException {

    private static final long serialVersionUID = 1L;

    public DocumentTooLargeException(String message) {
        super(message);
    }
}
