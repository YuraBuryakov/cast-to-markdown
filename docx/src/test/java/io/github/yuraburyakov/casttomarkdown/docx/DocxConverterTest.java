package io.github.yuraburyakov.casttomarkdown.docx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.UnsupportedFormatException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Through the public API, so the ServiceLoader registration of the module is tested too. */
class DocxConverterTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void headingsComeFromParagraphStyles() throws IOException {
        byte[] docx = TestDocx.builder()
                .styled("Title", "Guidance")
                .paragraph("Intro text.")
                .styled("Heading2", "Who can apply")
                .paragraph("Body text.")
                .bytes();

        assertThat(convert(docx)).isEqualTo("# Guidance\n\nIntro text.\n\n## Who can apply\n\nBody text.\n");
    }

    @Test
    void skipsEmptyParagraphsAndEscapesHashAtLineStart() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraph("First.")
                .paragraph("")
                .paragraph("   ")
                .paragraph("# not a heading")
                .bytes();

        assertThat(convert(docx)).isEqualTo("First.\n\n\\# not a heading\n");
    }

    @Test
    void bulletListWithNesting() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraph("Licensing authorities should:")
                .bullet(0, "apply checks consistently")
                .bullet(1, "to all applicants")
                .bullet(0, "keep records")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                Licensing authorities should:

                - apply checks consistently
                    - to all applicants
                - keep records
                """);
    }

    @Test
    void numberedListKeepsDocumentNumberingAfterHeading() throws IOException {
        // A court order (gov.uk BP15): one numbered list runs through all sections.
        byte[] docx = TestDocx.builder()
                .numbered(0, "An account shall be taken.")
                .numbered(0, "X shall produce an account.")
                .styled("Heading2", "Objections")
                .numbered(0, "Objections shall be made in writing.")
                .numbered(1, "with reasons")
                .numbered(0, "Responses shall be filed.")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                1. An account shall be taken.
                2. X shall produce an account.

                ## Objections

                3. Objections shall be made in writing.
                    1. with reasons
                4. Responses shall be filed.
                """);
    }

    @Test
    void tableBecomesMarkdownTable() throws IOException {
        byte[] docx = TestDocx.builder()
                .table(new String[] {"Level", "Impact"},
                        new String[] {"IAL1", "Low | Mod"},
                        new String[] {"IAL2", "High"})
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                | Level | Impact |
                | --- | --- |
                | IAL1 | Low \\| Mod |
                | IAL2 | High |
                """);
    }

    @Test
    void footnotesBecomeMarkdownFootnotes() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraphWithFootnote("Order for an account", "Often made with directions to be given later.")
                .paragraph("Next paragraph.")
                .bytes();

        String markdown = convert(docx);

        assertThat(markdown).startsWith("Order for an account[^");
        assertThat(markdown).containsPattern("\\[\\^(\\d+)][\\s\\S]*\\n\\[\\^\\1]: Often made with directions to be given later\\.\\n$");
    }

    @Test
    void readsFromPathToo() throws IOException {
        Path docx = Files.write(dir.resolve("letter.docx"), TestDocx.builder().paragraph("Dear Doctor").bytes());

        assertThat(converter.convert(docx).markdown()).isEqualTo("Dear Doctor\n");
    }

    @Test
    void wrapsDamagedDocxFile() throws IOException {
        // a valid package without a Word document: POI opens it, then XWPFDocument fails
        Path notWord = Files.write(dir.resolve("not-word.docx"), packageWithoutDocument());
        Path notZip = Files.writeString(dir.resolve("damaged.docx"), "this is not a DOCX");

        for (Path docx : new Path[] {notWord, notZip}) {
            assertThatThrownBy(() -> converter.convert(docx))
                    .isInstanceOf(DocumentConversionException.class)
                    .hasMessageContaining(docx.getFileName().toString());
        }
    }

    private static byte[] packageWithoutDocument() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"txt\" ContentType=\"text/plain\"/></Types>").getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("hello".getBytes(StandardCharsets.US_ASCII));
        }
        return bytes.toByteArray();
    }

    @Test
    void wrapsDamagedDocxAndDoesNotCloseTheStream() {
        TrackingStream in = new TrackingStream("this is not a DOCX".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> converter.convert(in, "damaged.docx"))
                .isInstanceOf(DocumentConversionException.class)
                .isNotInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("damaged.docx")
                .hasCauseInstanceOf(Exception.class);
        assertThat(in.closed).isFalse();
    }

    private String convert(byte[] docx) {
        return converter.convert(new ByteArrayInputStream(docx), "test.docx").markdown();
    }

    private static final class TrackingStream extends ByteArrayInputStream {

        boolean closed;

        TrackingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
