package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentTooLargeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CastToMarkdownSettingsTest {

    @TempDir
    Path dir;

    @Test
    void convertsDocumentOfExactlyTheLimit() throws IOException {
        Path pdf = pdf();
        CastToMarkdown converter = CastToMarkdown.builder().maxDocumentSize(Files.size(pdf)).build();

        assertThat(converter.convert(pdf).markdown()).isEqualTo("Text.\n");
        assertThat(converter.convert(new ByteArrayInputStream(Files.readAllBytes(pdf)), "doc.pdf").markdown())
                .isEqualTo("Text.\n");
    }

    @Test
    void rejectsFileLargerThanTheLimit() throws IOException {
        Path pdf = pdf();
        CastToMarkdown converter = CastToMarkdown.builder().maxDocumentSize(Files.size(pdf) - 1).build();

        assertThatThrownBy(() -> converter.convert(pdf))
                .isInstanceOf(DocumentTooLargeException.class)
                .hasMessageContaining("doc.pdf")
                .hasMessageContaining(String.valueOf(Files.size(pdf) - 1));
    }

    @Test
    void stopsReadingStreamAtTheLimitAndDoesNotCloseIt() {
        byte[] content = new byte[10_000];
        TrackingStream in = new TrackingStream(content);
        CastToMarkdown converter = CastToMarkdown.builder().maxDocumentSize(1_000).build();

        assertThatThrownBy(() -> converter.convert(in, "upload.pdf"))
                .isInstanceOf(DocumentTooLargeException.class)
                .hasMessageContaining("upload.pdf");
        assertThat(in.available()).as("bytes left in the stream").isEqualTo(10_000 - 1_001);
        assertThat(in.closed).isFalse();
    }

    @Test
    void largestLimitMeansNoLimit() throws IOException {
        Path pdf = pdf();
        CastToMarkdown converter = CastToMarkdown.builder().maxDocumentSize(Long.MAX_VALUE).build();

        assertThat(converter.convert(new ByteArrayInputStream(Files.readAllBytes(pdf)), "doc.pdf").markdown())
                .isEqualTo("Text.\n");
    }

    @Test
    void defaultLimitAcceptsNormalDocuments() throws IOException {
        assertThat(CastToMarkdown.create().convert(pdf()).markdown()).isEqualTo("Text.\n");
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> CastToMarkdown.builder().maxDocumentSize(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CastToMarkdown.builder().maxDocumentSize(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builtConverterDoesNotChangeWhenBuilderChanges() throws IOException {
        Path pdf = pdf();
        CastToMarkdown.Builder builder = CastToMarkdown.builder().maxDocumentSize(Files.size(pdf));
        CastToMarkdown converter = builder.build();

        builder.maxDocumentSize(1);

        assertThat(converter.convert(pdf).markdown()).isEqualTo("Text.\n");
    }

    private Path pdf() throws IOException {
        return TestPdf.builder().page().line(720, "Text.").writeTo(dir.resolve("doc.pdf"));
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
