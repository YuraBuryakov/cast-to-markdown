package io.github.yuraburyakov.casttomarkdown;

import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;

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
 * <p>Supported formats are those of the format modules on the class or module path: PDF ({@code .pdf})
 * with a text layer from {@code cast-to-markdown-pdf}, DOCX ({@code .docx}) from {@code cast-to-markdown-docx},
 * HTML ({@code .html}, {@code .htm}) from {@code cast-to-markdown-html}, plain text ({@code .txt}) from
 * {@code cast-to-markdown-txt}, CSV and TSV ({@code .csv}, {@code .tsv}) from {@code cast-to-markdown-csv}, Excel
 * workbooks ({@code .xlsx}) from {@code cast-to-markdown-xlsx}.
 * The format is detected by the file extension.
 * Scanned PDFs (pages are images, no text layer) are not supported: run OCR on them first.
 *
 * <p>Settings: {@link #create()} uses the defaults; {@link #builder()} changes them.
 * Documents larger than {@link Builder#maxDocumentSize(long)} (100 MiB by default) are rejected with
 * {@link DocumentTooLargeException} before they are parsed.
 *
 * <p>Instances are immutable and thread-safe. Create one instance and reuse it.
 */
public final class CastToMarkdown {

    private static final long DEFAULT_MAX_DOCUMENT_SIZE = 100L * 1024 * 1024;

    /** Lower-case file extension without the dot to the converter for that format. */
    private final Map<String, DocumentConverter> converters;
    private final long maxDocumentSize;

    private CastToMarkdown(Builder builder) {
        this.converters = loadConverters();
        this.maxDocumentSize = builder.maxDocumentSize;
    }

    /**
     * Creates a converter with the default settings; the same as {@code builder().build()}.
     *
     * @return an immutable, thread-safe converter
     */
    public static CastToMarkdown create() {
        return builder().build();
    }

    /**
     * Starts building a converter with custom settings.
     *
     * <pre>{@code
     * CastToMarkdown converter = CastToMarkdown.builder()
     *         .maxDocumentSize(20 * 1024 * 1024)
     *         .build();
     * }</pre>
     *
     * @return a new builder with the default settings
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Settings of a {@link CastToMarkdown}. Not thread-safe: configure it in one thread,
     * then share the built converter.
     */
    public static final class Builder {

        private long maxDocumentSize = DEFAULT_MAX_DOCUMENT_SIZE;

        private Builder() {
        }

        /**
         * Largest source document, in bytes, that is accepted. Default: 100 MiB.
         * A larger file or stream is rejected with {@link DocumentTooLargeException}; a stream is read
         * only up to the limit.
         *
         * <p>This limits the size of the source, not the heap a conversion uses. Memory use depends on
         * the format and the content and may be many times the source size: the parsers build an object
         * model of the document. For example, a 1.7 MB DOCX with 7.6 MB of text XML needed about 100 MB
         * of heap. A file ({@link CastToMarkdown#convert(Path)}) is read from disk as the parser needs it;
         * a stream ({@link CastToMarkdown#convert(InputStream, String)}) is first read into memory in full.
         *
         * @param bytes the limit; {@link Long#MAX_VALUE} for no limit
         * @return this builder
         * @throws IllegalArgumentException if {@code bytes} is not positive
         */
        public Builder maxDocumentSize(long bytes) {
            if (bytes <= 0) {
                throw new IllegalArgumentException("maxDocumentSize must be positive: " + bytes);
            }
            this.maxDocumentSize = bytes;
            return this;
        }

        /**
         * Creates a converter with these settings.
         *
         * @return an immutable, thread-safe converter
         */
        public CastToMarkdown build() {
            return new CastToMarkdown(this);
        }
    }

    /**
     * Converts the file at {@code path} into Markdown.
     *
     * @param path file to convert; must not be {@code null}
     * @return the converted document
     * @throws UnsupportedFormatException if the file format is not supported, or the PDF is a scan
     *         without a text layer
     * @throws DocumentTooLargeException if the file is larger than {@link Builder#maxDocumentSize(long)}
     * @throws DocumentConversionException if the file cannot be read or parsed
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public PreparedDocument convert(Path path) {
        Objects.requireNonNull(path, "path");

        Path fileName = path.getFileName();
        DocumentConverter converter = converter(fileName == null ? "" : fileName.toString(), path.toString());
        checkSize(path);
        return new PreparedDocument(converter.convert(path));
    }

    /**
     * Converts the document read from {@code input} into Markdown, for example an uploaded file
     * ({@code MultipartFile.getInputStream()} and {@code getOriginalFilename()} in Spring).
     *
     * <p>The stream is read to the end but <b>not closed</b>: the caller owns it and closes it,
     * also when this method throws. The content is read into memory in full before it is parsed;
     * see {@link Builder#maxDocumentSize(long)} for memory use.
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
     * @throws DocumentTooLargeException if the stream has more bytes than {@link Builder#maxDocumentSize(long)};
     *         it is read only up to the limit
     * @throws DocumentConversionException if the stream cannot be read or the document cannot be parsed
     * @throws NullPointerException if {@code input} or {@code fileName} is {@code null}
     */
    public PreparedDocument convert(InputStream input, String fileName) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(fileName, "fileName");

        return convertStream(input, fileName, null);
    }

    /**
     * As {@link #convert(InputStream, String)}, for a document read from {@code source}: a web page fetched by the
     * caller. Relative links of an HTML page are resolved against this address, unless the page names its own
     * base ({@code <base href>}); without it they stay text, unless the page gives its own address (canonical
     * link, {@code og:url}). Only an {@code http} or {@code https} address is used. Other formats ignore it.
     *
     * <pre>{@code
     * HttpResponse<InputStream> response = http.send(request, BodyHandlers.ofInputStream());
     * try (InputStream in = response.body()) {
     *     PreparedDocument page = converter.convert(in, "page.html", response.uri());
     * }
     * }</pre>
     *
     * @param input document content; must not be {@code null}; not closed by this method
     * @param fileName document file name, such as {@code "page.html"}: its extension selects the format,
     *        and it is used in error messages; must not be {@code null}
     * @param source the address the document was read from; must not be {@code null}
     * @return the converted document
     * @throws UnsupportedFormatException if the format is not supported (the stream is not read then),
     *         or the PDF is a scan without a text layer
     * @throws DocumentTooLargeException if the stream has more bytes than {@link Builder#maxDocumentSize(long)};
     *         it is read only up to the limit
     * @throws DocumentConversionException if the stream cannot be read or the document cannot be parsed
     * @throws NullPointerException if {@code input}, {@code fileName} or {@code source} is {@code null}
     */
    public PreparedDocument convert(InputStream input, String fileName, URI source) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(source, "source");

        return convertStream(input, fileName, source);
    }

    private PreparedDocument convertStream(InputStream input, String fileName, URI source) {
        DocumentConverter converter = converter(fileName, fileName);
        return new PreparedDocument(
                converter.convert(new LimitedInputStream(input, maxDocumentSize, fileName), fileName, source));
    }

    private void checkSize(Path path) {
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read file: " + path, e);
        }
        if (size > maxDocumentSize) {
            throw new DocumentTooLargeException(
                    "Document is larger than the limit of " + maxDocumentSize + " bytes (" + size + " bytes): " + path);
        }
    }

    /** The converter for the extension of {@code fileName}; {@code source} names the document in the error. */
    private DocumentConverter converter(String fileName, String source) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        DocumentConverter converter = converters.get(extension);
        if (converter == null) {
            throw new UnsupportedFormatException("Unsupported file format: " + source + (converters.isEmpty()
                    ? " (no format module found: add a dependency such as cast-to-markdown-pdf)"
                    : " (supported: " + String.join(", ", converters.keySet()) + ")"));
        }
        return converter;
    }

    /**
     * Converters of the format modules on the class path or module path, by extension.
     *
     * @throws IllegalStateException if two modules handle the same extension
     */
    private static Map<String, DocumentConverter> loadConverters() {
        Map<String, DocumentConverter> byExtension = new TreeMap<>();
        for (DocumentConverter converter : ServiceLoader.load(DocumentConverter.class, CastToMarkdown.class.getClassLoader())) {
            for (String extension : converter.extensions()) {
                DocumentConverter other = byExtension.putIfAbsent(extension, converter);
                if (other != null) {
                    throw new IllegalStateException("Two converters for ." + extension + ": "
                            + other.getClass().getName() + " and " + converter.getClass().getName());
                }
            }
        }
        return Collections.unmodifiableMap(byExtension); // sorted: the error message lists formats in a stable order
    }
}
