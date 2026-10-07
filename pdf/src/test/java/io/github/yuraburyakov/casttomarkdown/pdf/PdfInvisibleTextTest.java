package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Invisible text (rendering mode 3) under visible text is left out; alone on a page it is the text. */
class PdfInvisibleTextTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void invisibleLayerUnderVisibleTextIsLeftOut() throws IOException {
        // other-c6.pdf (Word 365), page 30: an older wording drawn invisible 0.7 pt above the visible one
        Path pdf = TestPdf.builder()
                .page()
                .invisibleLine(112, 700.7f, "Please ensure all fields are completed before pressing next.")
                .line(112, 700, "Please ensure all fields are complete before you press next.")
                .writeTo(dir.resolve("layers.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("Please ensure all fields are complete before you press next.\n");
    }

    @Test
    void invisibleTextAloneOnAPageIsTheText() throws IOException {
        // the text layer of a scan that went through OCR is invisible, over the picture of the page
        Path pdf = TestPdf.builder()
                .page()
                .invisibleLine(72, 700, "Text found by OCR.")
                .writeTo(dir.resolve("ocr.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("Text found by OCR.\n");
    }

    @Test
    void visibleTextDrawnTwiceForBoldnessComesOnce() throws IOException {
        // some generators make text bold by drawing it twice, slightly shifted
        Path pdf = TestPdf.builder()
                .page()
                .line(72, 700, "Bold heading")
                .line(72.3f, 700, "Bold heading")
                .writeTo(dir.resolve("bold.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("Bold heading\n");
    }
}
