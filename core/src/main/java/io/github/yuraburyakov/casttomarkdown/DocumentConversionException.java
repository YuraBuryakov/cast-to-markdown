package io.github.yuraburyakov.casttomarkdown;

/**
 * Thrown when a document cannot be converted: the file cannot be read, is damaged, encrypted,
 * or the parser fails. The original exception, if any, is available via {@link #getCause()}.
 */
public class DocumentConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what went wrong, with the document name
     */
    public DocumentConversionException(String message) {
        super(message);
    }

    /**
     * Creates the exception.
     *
     * @param message what went wrong, with the document name
     * @param cause the exception of the parser
     */
    public DocumentConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
