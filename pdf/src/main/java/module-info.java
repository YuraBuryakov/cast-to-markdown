/**
 * CastToMarkdown PDF format, based on Apache PDFBox. Nothing is exported: the converter is found
 * by the core module with {@link java.util.ServiceLoader}.
 */
module io.github.yuraburyakov.casttomarkdown.pdf {
    requires io.github.yuraburyakov.casttomarkdown;
    requires org.apache.pdfbox;

    provides io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter
            with io.github.yuraburyakov.casttomarkdown.pdf.PdfConverter;
}
