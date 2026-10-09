package io.github.yuraburyakov.casttomarkdown.html;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.internal.ConvertedDocument;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Converts HTML web pages to Markdown with jsoup.
 *
 * <p>Output: the main content of the page (the only {@code <main>}, else the body) without navigation, page
 * header and footer, sidebars, forms controls, scripts and hidden elements; headings, paragraphs, lists,
 * quotes, code blocks, tables as Markdown tables, links as {@code [text](url)}. Relative links are resolved
 * against {@code <base>}, the address the caller read the page from, or the address the page gives itself
 * (canonical link, {@code og:url}); without one they stay text. Metadata: the {@code <title>},
 * {@code <meta name="author">} and {@code <html lang>}. Images are left out.
 *
 * <p>Stateless and thread-safe: every call works on its own document.
 */
public final class HtmlConverter implements DocumentConverter {

    /** Creates the converter; {@link java.util.ServiceLoader} calls it. */
    public HtmlConverter() {
    }

    @Override
    public List<String> extensions() {
        return List.of("html", "htm");
    }

    /** The charset comes from a byte order mark or a {@code <meta>} tag, else UTF-8. */
    @Override
    public ConvertedDocument convert(Path path) {
        try {
            return render(Jsoup.parse(path.toFile(), null, ""), null);
        } catch (IOException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read HTML: " + path, e);
        }
    }

    @Override
    public ConvertedDocument convert(InputStream input, String name, URI source) {
        try {
            return render(Jsoup.parse(new ByteArrayInputStream(input.readAllBytes()), null, ""), source);
        } catch (IOException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read HTML: " + name, e);
        }
    }

    private static ConvertedDocument render(Document document, URI source) {
        // read before rendering, which removes elements
        String title = document.title();
        String author = document.select("meta[name=author]").attr("content");
        String language = document.select("html").attr("lang");
        return new ConvertedDocument(Markdown.normalize(new HtmlRenderer(document, source).render()), title, author,
                language);
    }
}
