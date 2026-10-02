package io.github.yuraburyakov.casttomarkdown;

import java.nio.file.Path;
import java.util.Locale;
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
 * <p>Supported formats: PDF ({@code .pdf}). The format is detected by the file extension.
 *
 * <p>Instances are immutable and thread-safe. Create one instance and reuse it.
 */
public final class CastToMarkdown {

    private final PdfConverter pdfConverter;

    private CastToMarkdown(PdfConverter pdfConverter) {
        this.pdfConverter = pdfConverter;
    }

    /**
     * Creates a converter with the default settings.
     */
    public static CastToMarkdown create() {
        return new CastToMarkdown(new PdfConverter());
    }

    /**
     * Converts the file at {@code path} into Markdown.
     *
     * @param path file to convert; must not be {@code null}
     * @return the converted document
     * @throws UnsupportedFormatException if the file format is not supported
     * @throws DocumentConversionException if the file cannot be read or parsed
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public PreparedDocument convert(Path path) {
        Objects.requireNonNull(path, "path");

        if (!hasExtension(path, ".pdf")) {
            throw new UnsupportedFormatException("Unsupported file format: " + path);
        }
        return new PreparedDocument(pdfConverter.convert(path));
    }

    private static boolean hasExtension(Path path, String extension) {
        Path fileName = path.getFileName();
        return fileName != null
                && fileName.toString().toLowerCase(Locale.ROOT).endsWith(extension);
    }
}
