package io.github.yuraburyakov.casttomarkdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The core module alone has no format modules: every format is unsupported, with a hint how to fix it. */
class CastToMarkdownWithoutFormatsTest {

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void explainsThatAFormatModuleIsMissing() {
        assertThatThrownBy(() -> converter.convert(Path.of("report.pdf")))
                .isInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("report.pdf")
                .hasMessageContaining("cast-to-markdown-pdf");
    }

    @Test
    void streamIsNotReadWhenNoModuleHandlesTheFormat() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> converter.convert(in, "report.pdf"))
                .isInstanceOf(UnsupportedFormatException.class);
        assertThat(in.available()).isEqualTo(3);
    }
}
