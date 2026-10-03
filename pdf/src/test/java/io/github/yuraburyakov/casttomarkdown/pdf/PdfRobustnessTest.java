package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Damaged PDFs make PDFBox fail with unchecked exceptions; callers must still get our exception. */
class PdfRobustnessTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void uncheckedParserFailureBecomesDocumentConversionException() throws IOException {
        // found by fuzzing: two "cm" operators whose product is infinite make PDFBox's Matrix throw
        // IllegalArgumentException ("Multiplying two matrices produces illegal values")
        String huge = "3" + "0".repeat(38) + ".0"; // PDF numbers have no exponent notation
        String cm = huge + " 0 0 " + huge + " 0 0 cm ";
        Path pdf = pdfWithContent("huge-matrix.pdf", cm + cm + "BT /F1 12 Tf 10 10 Td (text) Tj ET");

        assertThatThrownBy(() -> converter.convert(pdf))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("huge-matrix.pdf")
                .hasCauseInstanceOf(IllegalArgumentException.class);
        try (InputStream in = Files.newInputStream(pdf)) {
            assertThatThrownBy(() -> converter.convert(in, "huge-matrix.pdf"))
                    .isInstanceOf(DocumentConversionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    private Path pdfWithContent(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            page.setResources(resources);
            PDStream stream = new PDStream(document);
            try (OutputStream out = stream.createOutputStream()) {
                out.write(content.getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            document.save(file.toFile());
        }
        return file;
    }
}
