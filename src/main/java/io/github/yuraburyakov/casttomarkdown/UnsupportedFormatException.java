package io.github.yuraburyakov.casttomarkdown;

/**
 * Thrown when the document format is not supported.
 */
public class UnsupportedFormatException extends DocumentConversionException {

    private static final long serialVersionUID = 1L;

    public UnsupportedFormatException(String message) {
        super(message);
    }
}
