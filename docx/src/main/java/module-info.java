/**
 * CastToMarkdown DOCX format, based on Apache POI. Nothing is exported: the converter is found
 * by the core module with {@link java.util.ServiceLoader}.
 */
module io.github.yuraburyakov.casttomarkdown.docx {
    requires io.github.yuraburyakov.casttomarkdown;
    requires org.apache.poi.ooxml;

    provides io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter
            with io.github.yuraburyakov.casttomarkdown.docx.DocxConverter;
}
