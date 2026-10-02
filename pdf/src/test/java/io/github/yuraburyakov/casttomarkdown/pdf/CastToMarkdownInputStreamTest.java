package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.UnsupportedFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract of {@code convert(InputStream, String)}: the caller owns the stream, the file name selects the format. */
class CastToMarkdownInputStreamTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void givesSameResultAsPath() throws IOException {
        Path pdf = TestPdf.builder()
                .page().line(720, "First paragraph.").line(600, "Second paragraph.")
                .writeTo(dir.resolve("doc.pdf"));

        try (InputStream in = Files.newInputStream(pdf)) {
            assertThat(converter.convert(in, "doc.pdf").markdown()).isEqualTo(converter.convert(pdf).markdown());
        }
    }

    @Test
    void doesNotCloseTheStream() throws IOException {
        TrackingStream in = new TrackingStream(Files.readAllBytes(pdf("doc.pdf")));

        converter.convert(in, "doc.pdf");

        assertThat(in.closed).isFalse();
    }

    @Test
    void doesNotCloseTheStreamWhenConversionFails() {
        TrackingStream in = new TrackingStream("this is not a PDF".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> converter.convert(in, "damaged.pdf"))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("damaged.pdf");
        assertThat(in.closed).isFalse();
    }

    @Test
    void detectsFormatByFileNameIgnoringCase() throws IOException {
        try (InputStream in = Files.newInputStream(pdf("doc.pdf"))) {
            assertThat(converter.convert(in, "REPORT.PDF").markdown()).isEqualTo("Text.\n");
        }
    }

    @Test
    void rejectsUnsupportedFormatWithoutReadingTheStream() {
        TrackingStream in = new TrackingStream(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> converter.convert(in, "notes.docx"))
                .isInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("notes.docx");
        assertThat(in.available()).isEqualTo(3);
    }

    @Test
    void wrapsReadError() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };

        assertThatThrownBy(() -> converter.convert(failing, "upload.pdf"))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("upload.pdf")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> converter.convert(null, "doc.pdf")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> converter.convert(new ByteArrayInputStream(new byte[0]), null))
                .isInstanceOf(NullPointerException.class);
    }

    private Path pdf(String name) throws IOException {
        return TestPdf.builder().page().line(720, "Text.").writeTo(dir.resolve(name));
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
