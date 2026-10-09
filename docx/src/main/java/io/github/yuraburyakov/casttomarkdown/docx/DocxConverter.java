package io.github.yuraburyakov.casttomarkdown.docx;

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
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackageProperties;
import org.apache.poi.xwpf.usermodel.XWPFDefaultRunStyle;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

/**
 * Converts DOCX (Word 2007+) to Markdown with Apache POI.
 *
 * <p>Output: headings from the paragraph styles ({@code Title}, {@code Heading 1..6}), paragraphs,
 * bullet and numbered lists with nesting and the document's numbering, tables as Markdown tables,
 * footnotes as Markdown footnotes, external links as {@code [text](url)}. Running headers and footers are
 * not part of the body and are left out.
 * Bold text without a heading style is not a heading: in real documents that is often form labels.
 *
 * <p>Stateless and thread-safe: every call works on its own document.
 */
public final class DocxConverter implements DocumentConverter {

    /** Creates the converter; {@link java.util.ServiceLoader} calls it. */
    public DocxConverter() {
    }

    @Override
    public List<String> extensions() {
        return List.of("docx");
    }

    /**
     * The file is read by POI with random access, not loaded into memory first; it is opened read-only
     * and released when the method returns, also on error.
     */
    @Override
    public ConvertedDocument convert(Path path) {
        return render(() -> open(path), path.toString());
    }

    @Override
    public ConvertedDocument convert(InputStream input, String name, URI source) {
        byte[] bytes;
        try {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read DOCX: " + name, e);
        }
        // POI may close the stream it gets, so it reads a copy, never the caller's stream
        return render(() -> new XWPFDocument(new ByteArrayInputStream(bytes)), name);
    }

    private static XWPFDocument open(Path path) throws IOException, InvalidFormatException {
        OPCPackage docx = OPCPackage.open(path.toFile(), PackageAccess.READ);
        try {
            return new XWPFDocument(docx);
        } catch (IOException | RuntimeException e) {
            docx.revert();
            throw e;
        }
    }

    /**
     * Renders the document with its metadata and closes it. Markdown is normalized only after it is closed, when the
     * POI document is no longer reachable: together with file-based reading, a DOCX with 7.6 MB of XML
     * peaks at 99 MB instead of 115 MB.
     * POI reports damaged files with many unchecked exception types, so all of them are wrapped.
     */
    private static ConvertedDocument render(Source source, String name) {
        ConvertedDocument rendered;
        try (XWPFDocument document = source.open()) {
            PackageProperties properties = document.getPackage().getPackageProperties();
            rendered = new ConvertedDocument(new DocxRenderer(document).render(),
                    properties.getTitleProperty().orElse(null), properties.getCreatorProperty().orElse(null),
                    properties.getLanguageProperty().orElseGet(() -> defaultLanguage(document)));
        } catch (EncryptedDocumentException | OLE2NotOfficeXmlFileException e) {
            // a password-protected DOCX is an OLE2 container, like an old .doc renamed to .docx
            throw new DocumentConversionException(
                    "DOCX is password-protected, or is an old Word .doc file, which is not supported: " + name, e);
        } catch (IOException | InvalidFormatException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read DOCX: " + name, e);
        }
        return new ConvertedDocument(Markdown.normalize(rendered.markdown()), rendered.title(), rendered.author(),
                rendered.language());
    }

    /**
     * The language of the default text style ({@code w:docDefaults}), where Word writes the language of the
     * document; its core properties rarely have one.
     */
    private static String defaultLanguage(XWPFDocument document) {
        XWPFStyles styles = document.getStyles();
        XWPFDefaultRunStyle style = styles == null ? null : styles.getDefaultRunStyle();
        CTRPr properties = style == null ? null : style.getRPr();
        return properties == null || properties.sizeOfLangArray() == 0 ? null : properties.getLangArray(0).getVal();
    }

    @FunctionalInterface
    private interface Source {
        XWPFDocument open() throws IOException, InvalidFormatException;
    }
}
