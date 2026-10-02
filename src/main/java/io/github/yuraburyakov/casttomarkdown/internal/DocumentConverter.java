package io.github.yuraburyakov.casttomarkdown.internal;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import java.nio.file.Path;

/**
 * Converts one document format into Markdown. One implementation per format, each in its own package.
 *
 * <p>Internal contract, not API: it may change in any version. Implementations must be stateless
 * and thread-safe, because one instance is shared by all calls of a {@code CastToMarkdown} instance.
 */
public interface DocumentConverter {

    /**
     * Converts the file into Markdown.
     *
     * @throws DocumentConversionException if the file cannot be read or parsed
     */
    String convert(Path path);
}
