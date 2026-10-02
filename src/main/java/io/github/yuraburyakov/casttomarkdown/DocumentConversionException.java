package io.github.yuraburyakov.casttomarkdown;

/**
 * Thrown when a document cannot be converted: the file cannot be read, is damaged, encrypted,
 * or the parser fails. The original exception, if any, is available via {@link #getCause()}.
 */
public class DocumentConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentConversionException(String message) {
        super(message);
    }

    public DocumentConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
