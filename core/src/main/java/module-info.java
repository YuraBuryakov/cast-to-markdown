/**
 * CastToMarkdown core: the API. Formats come from separate modules ({@code cast-to-markdown-pdf}, ...)
 * found with {@link java.util.ServiceLoader}.
 * Only {@code io.github.yuraburyakov.casttomarkdown} is API; {@code internal} is exported only to the
 * format modules.
 */
// "module" lint: the format modules are built after this one, so javac warns they are not found.
@SuppressWarnings("module")
module io.github.yuraburyakov.casttomarkdown {
    exports io.github.yuraburyakov.casttomarkdown;
    exports io.github.yuraburyakov.casttomarkdown.internal
            to io.github.yuraburyakov.casttomarkdown.pdf, io.github.yuraburyakov.casttomarkdown.docx,
            io.github.yuraburyakov.casttomarkdown.html, io.github.yuraburyakov.casttomarkdown.txt,
            io.github.yuraburyakov.casttomarkdown.csv, io.github.yuraburyakov.casttomarkdown.xlsx;

    uses io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
}
