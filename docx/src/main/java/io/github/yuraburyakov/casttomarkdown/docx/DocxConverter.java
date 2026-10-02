package io.github.yuraburyakov.casttomarkdown.docx;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Converts DOCX (Word 2007+) to Markdown with Apache POI.
 *
 * <p>Output: headings from the paragraph styles ({@code Title}, {@code Heading 1..6}), paragraphs,
 * bullet and numbered lists with nesting and the document's numbering, tables as Markdown tables,
 * footnotes as Markdown footnotes. Running headers and footers are not part of the body and are left out.
 * Bold text without a heading style is not a heading: in real documents that is often form labels.
 *
 * <p>Stateless and thread-safe: every call works on its own document.
 */
public final class DocxConverter implements DocumentConverter {

    @Override
    public List<String> extensions() {
        return List.of("docx");
    }

    @Override
    public String convert(Path path) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read DOCX: " + path, e);
        }
        return convert(bytes, path.toString());
    }

    @Override
    public String convert(InputStream input, String name) {
        byte[] bytes;
        try {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read DOCX: " + name, e);
        }
        return convert(bytes, name);
    }

    /**
     * The document is read from bytes, never from the caller's stream: POI may close the stream it gets.
     * POI reports damaged files with many unchecked exception types, so all of them are wrapped.
     */
    private static String convert(byte[] bytes, String name) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return Markdown.normalize(new DocxRenderer(document).render());
        } catch (EncryptedDocumentException e) {
            throw new DocumentConversionException("DOCX is encrypted: " + name, e);
        } catch (IOException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read DOCX: " + name, e);
        }
    }
}
