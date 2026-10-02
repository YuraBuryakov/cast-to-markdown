package io.github.yuraburyakov.casttomarkdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CastToMarkdownTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void keepsLinesOfOneParagraphOnSeparateLines() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .line(720, "First line.")
                .line(706, "Second line.")
                .writeTo(dir.resolve("lines.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("First line.\nSecond line.\n");
    }

    @Test
    void separatesParagraphsWithBlankLine() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .line(720, "First paragraph.")
                .line(600, "Second paragraph.")
                .writeTo(dir.resolve("paragraphs.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("First paragraph.\n\nSecond paragraph.\n");
    }

    @Test
    void separatesPagesWithBlankLine() throws IOException {
        Path pdf = TestPdf.builder()
                .page().line(720, "Page one.")
                .page().line(720, "Page two.")
                .writeTo(dir.resolve("pages.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("Page one.\n\nPage two.\n");
    }

    @Test
    void returnsEmptyMarkdownForPdfWithoutText() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .writeTo(dir.resolve("empty.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEmpty();
    }

    @Test
    void rejectsScannedPdfWithoutTextLayer() throws IOException {
        Path pdf = TestPdf.builder()
                .page().image()
                .page().image()
                .writeTo(dir.resolve("scan.pdf"));

        assertThatThrownBy(() -> converter.convert(pdf))
                .isInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("no text layer")
                .hasMessageContaining("OCR")
                .hasMessageContaining("scan.pdf");
    }

    @Test
    void convertsPdfWithTextAndImages() throws IOException {
        Path pdf = TestPdf.builder()
                .page().image().line(720, "Text next to a picture.")
                .writeTo(dir.resolve("illustrated.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("Text next to a picture.\n");
    }

    @Test
    void detectsPdfExtensionIgnoringCase() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .line(720, "Upper-case extension.")
                .writeTo(dir.resolve("REPORT.PDF"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("Upper-case extension.\n");
    }

    @Test
    void rejectsUnsupportedFormat() throws IOException {
        Path docx = Files.writeString(dir.resolve("document.docx"), "not supported yet");

        assertThatThrownBy(() -> converter.convert(docx))
                .isInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("document.docx");
    }

    @Test
    void wrapsMissingFileError() {
        Path missing = dir.resolve("missing.pdf");

        assertThatThrownBy(() -> converter.convert(missing))
                .isInstanceOf(DocumentConversionException.class)
                .isNotInstanceOf(UnsupportedFormatException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsDamagedPdfError() throws IOException {
        Path damaged = Files.write(dir.resolve("damaged.pdf"),
                "this is not a PDF".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> converter.convert(damaged))
                .isInstanceOf(DocumentConversionException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void rejectsNullPath() {
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class);
    }
}
