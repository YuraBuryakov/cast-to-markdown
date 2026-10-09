/**
 * CastToMarkdown TXT format, without parser dependencies. Nothing is exported: the converter is found by the core
 * module with {@link java.util.ServiceLoader}.
 */
module io.github.yuraburyakov.casttomarkdown.txt {
    requires io.github.yuraburyakov.casttomarkdown;

    provides io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter
            with io.github.yuraburyakov.casttomarkdown.txt.TxtConverter;
}
