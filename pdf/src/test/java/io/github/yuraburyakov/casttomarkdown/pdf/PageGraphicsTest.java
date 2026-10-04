package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageGraphicsTest {

    @TempDir
    Path dir;

    @Test
    void boxesAreMeasuredFromTheTopOfThePage() throws IOException {
        Path pdf = TestPdf.builder().page().rect(100, 500, 200, 100).rule(72, 300, 400).writeTo(dir.resolve("g.pdf"));

        List<PageGraphics.Box> boxes = boxes(pdf);

        assertThat(boxes).hasSize(2);
        PageGraphics.Box rect = boxes.get(0);
        assertThat(rect.left()).isCloseTo(100f, within(0.5f));
        assertThat(rect.right()).isCloseTo(300f, within(0.5f));
        assertThat(rect.top()).isCloseTo(192f, within(0.5f));
        assertThat(rect.bottom()).isCloseTo(292f, within(0.5f));
        assertThat(rect.thin()).isFalse();
        assertThat(boxes.get(1).thin()).isTrue();
    }

    @Test
    void imageIsABox() throws IOException {
        // TestPdf draws the picture at (72, 400), 200 x 200 points
        Path pdf = TestPdf.builder().page().image().writeTo(dir.resolve("image.pdf"));

        assertThat(boxes(pdf)).singleElement().satisfies(box -> {
            assertThat(box.left()).isCloseTo(72f, within(0.5f));
            assertThat(box.top()).isCloseTo(192f, within(0.5f));
            assertThat(box.bottom()).isCloseTo(392f, within(0.5f));
        });
    }

    @Test
    void keepsOneBoxMoreThanTheLimit() throws IOException {
        // untrusted input: millions of boxes would fill the memory before any limit is checked
        TestPdf builder = TestPdf.builder().page();
        for (int i = 0; i < PageGraphics.MAX_BOXES + 50; i++) {
            builder.rect(i % 100 * 5, i / 100 * 5, 2, 2);
        }
        Path pdf = builder.writeTo(dir.resolve("many.pdf"));

        assertThat(boxes(pdf)).hasSize(PageGraphics.MAX_BOXES + 1);
    }

    @Test
    void textIsNotGraphics() throws IOException {
        Path pdf = TestPdf.builder().page().line(700, "Only text").writeTo(dir.resolve("text.pdf"));

        assertThat(boxes(pdf)).isEmpty();
    }

    private static List<PageGraphics.Box> boxes(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return PageGraphics.of(document.getPage(0));
        }
    }
}
