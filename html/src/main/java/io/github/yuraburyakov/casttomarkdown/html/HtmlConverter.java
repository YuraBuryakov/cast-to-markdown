package io.github.yuraburyakov.casttomarkdown.html;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * against the address the page gives itself ({@code <base>}, canonical link, {@code og:url}); without one
 * they stay text. Images are left out.
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
    public String convert(Path path) {
        try {
            return render(Jsoup.parse(path.toFile(), null, ""));
        } catch (IOException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read HTML: " + path, e);
        }
    }

    @Override
    public String convert(InputStream input, String name) {
        try {
            return render(Jsoup.parse(new ByteArrayInputStream(input.readAllBytes()), null, ""));
        } catch (IOException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read HTML: " + name, e);
        }
    }

    private static String render(Document document) {
        return Markdown.normalize(new HtmlRenderer(document).render());
    }
}
