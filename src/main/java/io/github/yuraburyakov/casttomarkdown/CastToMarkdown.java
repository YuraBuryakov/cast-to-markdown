package io.github.yuraburyakov.casttomarkdown;

import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.pdf.PdfConverter;
import java.io.InputStream;
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
 * <p>Input is a file {@link Path}, or an {@link InputStream} with the file name; a stream is never
 * closed by this class.
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

        Path fileName = path.getFileName();
        DocumentConverter converter = converter(fileName == null ? "" : fileName.toString(), path.toString());
        return new PreparedDocument(converter.convert(path));
    }

    /**
     * Converts the document read from {@code input} into Markdown, for example an uploaded file
     * ({@code MultipartFile.getInputStream()} and {@code getOriginalFilename()} in Spring).
     *
     * <p>The stream is read to the end but <b>not closed</b>: the caller owns it and closes it,
     * also when this method throws. The whole document is held in memory while it is converted.
     *
     * <pre>{@code
     * try (InputStream in = upload.getInputStream()) {
     *     String markdown = converter.convert(in, upload.getOriginalFilename()).markdown();
     * }
     * }</pre>
     *
     * @param input document content; must not be {@code null}; not closed by this method
     * @param fileName document file name, such as {@code "report.pdf"}: its extension selects the format,
     *        and it is used in error messages; must not be {@code null}
     * @return the converted document
     * @throws UnsupportedFormatException if the format is not supported (the stream is not read then),
     *         or the PDF is a scan without a text layer
     * @throws DocumentConversionException if the stream cannot be read or the document cannot be parsed
     * @throws NullPointerException if {@code input} or {@code fileName} is {@code null}
     */
    public PreparedDocument convert(InputStream input, String fileName) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(fileName, "fileName");

        DocumentConverter converter = converter(fileName, fileName);
        return new PreparedDocument(converter.convert(input, fileName));
    }

    /** The converter for the extension of {@code fileName}; {@code source} names the document in the error. */
    private DocumentConverter converter(String fileName, String source) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        DocumentConverter converter = converters.get(extension);
        if (converter == null) {
            throw new UnsupportedFormatException("Unsupported file format: " + source);
        }
        return converter;
    }
}
