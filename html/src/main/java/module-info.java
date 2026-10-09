/**
 * CastToMarkdown HTML format, based on jsoup. Nothing is exported: the converter is found by the core
 * module with {@link java.util.ServiceLoader}.
 */
module io.github.yuraburyakov.casttomarkdown.html {
    requires io.github.yuraburyakov.casttomarkdown;
    requires org.jsoup;

    provides io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter
            with io.github.yuraburyakov.casttomarkdown.html.HtmlConverter;
}
