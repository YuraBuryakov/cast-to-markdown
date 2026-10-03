/**
 * CastToMarkdown PDF format, based on Apache PDFBox. Nothing is exported: the converter is found
 * by the core module with {@link java.util.ServiceLoader}.
 */
module io.github.yuraburyakov.casttomarkdown.pdf {
    requires io.github.yuraburyakov.casttomarkdown;
    requires org.apache.pdfbox;
    // PDFBox 3 is an automatic module and cannot declare that it needs commons-logging, an explicit module:
    // without this line it is left out of the module graph and PDFBox fails with NoClassDefFoundError.
    requires org.apache.commons.logging;

    provides io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter
            with io.github.yuraburyakov.casttomarkdown.pdf.PdfConverter;
}
