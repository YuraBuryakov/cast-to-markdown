package io.github.yuraburyakov.casttomarkdown.internal;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Converts one document format into Markdown. One implementation per format module
 * ({@code cast-to-markdown-pdf}, ...), found with {@link java.util.ServiceLoader}: the module declares
 * {@code provides DocumentConverter with ...} and lists the class in
 * {@code META-INF/services/io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter} for the class path.
 *
 * <p>Internal contract, not API: it may change in any version, and the package is exported only to the
 * format modules. Implementations need a public no-argument constructor, must be stateless and
 * thread-safe, because one instance is shared by all calls of a {@code CastToMarkdown} instance.
 */
public interface DocumentConverter {

    /** File extensions this converter handles: lower case, without the dot, e.g. {@code "pdf"}. */
    List<String> extensions();

    /**
     * Converts the file into Markdown.
     *
     * @throws DocumentConversionException if the file cannot be read or parsed
     */
    String convert(Path path);

    /**
     * Converts the document read from {@code input} into Markdown. Reads the stream to the end
     * and does not close it.
     *
     * @param name document name for error messages
     * @throws DocumentConversionException if the stream cannot be read or the document cannot be parsed
     */
    String convert(InputStream input, String name);
}
