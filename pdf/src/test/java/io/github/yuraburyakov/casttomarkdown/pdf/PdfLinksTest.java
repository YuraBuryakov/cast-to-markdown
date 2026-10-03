package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Link annotations to web addresses become Markdown links, in tagged and untagged PDFs alike. */
class PdfLinksTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void webLinkBecomesMarkdownLink() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .link(720, "See ", "the full guide", "https://example.org/guide", " for details.")
                .writeTo(dir.resolve("link.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("See [the full guide](https://example.org/guide) for details.\n");
    }

    @Test
    void linkBoxEndingInsideAWordDoesNotSplitIt() throws IOException {
        // gov.uk Word PDF: the box of "Apply to the Windrush Scheme" ends after the "t" of the next "to"
        Path pdf = TestPdf.builder()
                .page()
                .link(720, "", "Apply to the Scheme t", "https://example.org/apply", "o get proof.")
                .writeTo(dir.resolve("mid-word.pdf"));

        assertThat(converter.convert(pdf).markdown())
                .isEqualTo("[Apply to the Scheme to](https://example.org/apply) get proof.\n");
    }

    @Test
    void internalUnsafeAndSelfLinksStayText() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .link(720, "Go to ", "Introduction", null, ".")
                .link(690, "Click ", "here", "javascript:alert(1)", ".")
                .link(660, "Visit ", "https://example.org", "https://example.org", " today.")
                .writeTo(dir.resolve("plain.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                Go to Introduction.

                Click here.

                Visit https://example.org today.
                """);
    }
}
