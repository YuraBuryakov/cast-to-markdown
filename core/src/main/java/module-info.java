/**
 * CastToMarkdown core: the API. Formats come from separate modules ({@code cast-to-markdown-pdf}, ...)
 * found with {@link java.util.ServiceLoader}.
 * Only {@code io.github.yuraburyakov.casttomarkdown} is API; {@code internal} is exported only to the
 * format modules.
 */
module io.github.yuraburyakov.casttomarkdown {
    exports io.github.yuraburyakov.casttomarkdown;
    exports io.github.yuraburyakov.casttomarkdown.internal to io.github.yuraburyakov.casttomarkdown.pdf;

    uses io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
}
