package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
    void internalAndUnsafeLinksStayText() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .link(720, "Go to ", "Introduction", null, ".")
                .link(690, "Click ", "here", "javascript:alert(1)", ".")
                .writeTo(dir.resolve("plain.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                Go to Introduction.

                Click here.
                """);
    }

    @Test
    void linkToItselfIsAnAutolink() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .link(720, "Visit ", "https://example.org", "https://example.org", " today.")
                .link(690, "", "https://example.org/c", "https://example.org/c", " first")
                // RFC 9562 writes the brackets itself: that is an autolink already
                .link(660, "at <", "https://example.org/d", "https://example.org/d", ">.")
                .writeTo(dir.resolve("self.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                Visit <https://example.org> today.

                <https://example.org/c> first

                at <https://example.org/d>.
                """);
    }

    @Test
    void linkAfterARaisedFootnoteNumberIsAnAutolink() throws IOException {
        // arXiv ResNet: "1http://image-net.org/..." was plain text, no GFM autolink right after a digit
        Path pdf = TestPdf.builder()
                .page()
                .scriptLine(720, "", "1", 4, "https://example.org/a", "https://example.org/a")
                .writeTo(dir.resolve("footnote-link.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("¹<https://example.org/a>\n");
    }

    @Test
    void linkBrokenOverLinesIsOneLink() {
        // Chrome (Wikipedia): the link box of each line becomes a link of its own
        assertThat(PdfConverter.joinSplitLinks("""
                its [Distributed Computing](https://en.wikipedia.org/wiki/DCE)\s
                [Environment](https://en.wikipedia.org/wiki/DCE) (DCE), and
                [a](https://example.org/x)
                [three](https://example.org/x)
                [lines](https://example.org/x) link"""))
                .isEqualTo("""
                        its [Distributed Computing
                        Environment](https://en.wikipedia.org/wiki/DCE) (DCE), and
                        [a
                        three
                        lines](https://example.org/x) link""");
    }

    @Test
    void wordSplitInsideALinkBrokenOverLinesIsJoined() {
        // arXiv 1810.04805, a reference title: "fast un-" / "supervised" are two links until they are one
        List<Line> lines = List.of(
                new Line(1, 72, 700, 10, false, "objectives for fast [un-](https://arxiv.org/abs/1)"),
                new Line(1, 72, 712, 10, false, "[supervised sentence](https://arxiv.org/abs/1). CoRR,"),
                new Line(1, 72, 724, 10, false, "and unsupervised data."));

        assertThat(PdfConverter.toMarkdown(lines)).isEqualTo("""
                objectives for fast [unsupervised
                sentence](https://arxiv.org/abs/1). CoRR,
                and unsupervised data.""");
    }

    @Test
    void linksToOtherAddressesOnNextLinesStayApart() {
        String text = "[first](https://example.org/a)\n[second](https://example.org/b)";

        assertThat(PdfConverter.joinSplitLinks(text)).isEqualTo(text);
    }
}
