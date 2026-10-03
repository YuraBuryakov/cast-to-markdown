package io.github.yuraburyakov.casttomarkdown.docx;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

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
    public String convert(Path path) {
        return Markdown.normalize(render(() -> open(path), path.toString()));
    }

    @Override
    public String convert(InputStream input, String name) {
        byte[] bytes;
        try {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read DOCX: " + name, e);
        }
        // POI may close the stream it gets, so it reads a copy, never the caller's stream
        return Markdown.normalize(render(() -> new XWPFDocument(new ByteArrayInputStream(bytes)), name));
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
     * Renders the document and closes it. Markdown is normalized only after this returns, when the
     * POI document is no longer reachable: together with file-based reading, a DOCX with 7.6 MB of XML
     * peaks at 99 MB instead of 115 MB.
     * POI reports damaged files with many unchecked exception types, so all of them are wrapped.
     */
    private static String render(Source source, String name) {
        try (XWPFDocument document = source.open()) {
            return new DocxRenderer(document).render();
        } catch (EncryptedDocumentException e) {
            throw new DocumentConversionException("DOCX is encrypted: " + name, e);
        } catch (IOException | InvalidFormatException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read DOCX: " + name, e);
        }
    }

    @FunctionalInterface
    private interface Source {
        XWPFDocument open() throws IOException, InvalidFormatException;
    }
}
