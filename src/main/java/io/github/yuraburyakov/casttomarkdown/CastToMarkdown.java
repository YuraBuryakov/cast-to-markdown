package io.github.yuraburyakov.casttomarkdown;

import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.pdf.PdfConverter;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Converts documents into Markdown.
 *
 * <pre>{@code
 * CastToMarkdown converter = CastToMarkdown.create();
 * PreparedDocument document = converter.convert(Path.of("report.pdf"));
 * String markdown = document.markdown();
 * }</pre>
 *
 * <p>Supported formats: PDF ({@code .pdf}) with a text layer. The format is detected by the file extension.
 * Scanned PDFs (pages are images, no text layer) are not supported: run OCR on them first.
 *
 * <p>Instances are immutable and thread-safe. Create one instance and reuse it.
 */
public final class CastToMarkdown {

    /** Lower-case file extension without the dot to the converter for that format. */
    private final Map<String, DocumentConverter> converters;

    private CastToMarkdown(Map<String, DocumentConverter> converters) {
        this.converters = converters;
    }

    /**
     * Creates a converter with the default settings.
     */
    public static CastToMarkdown create() {
        return new CastToMarkdown(Map.of("pdf", new PdfConverter()));
    }

    /**
     * Converts the file at {@code path} into Markdown.
     *
     * @param path file to convert; must not be {@code null}
     * @return the converted document
     * @throws UnsupportedFormatException if the file format is not supported, or the PDF is a scan
     *         without a text layer
     * @throws DocumentConversionException if the file cannot be read or parsed
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public PreparedDocument convert(Path path) {
        Objects.requireNonNull(path, "path");

        DocumentConverter converter = converters.get(extension(path));
        if (converter == null) {
            throw new UnsupportedFormatException("Unsupported file format: " + path);
        }
        return new PreparedDocument(converter.convert(path));
    }

    /** Lower-case extension without the dot, or an empty string when the file name has none. */
    private static String extension(Path path) {
        Path fileName = path.getFileName();
        String name = fileName == null ? "" : fileName.toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
