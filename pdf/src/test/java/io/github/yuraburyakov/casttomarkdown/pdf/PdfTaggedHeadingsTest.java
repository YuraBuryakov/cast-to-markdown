package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Headings of a tagged PDF taken from its structure tree, where the font does not tell them from the text. */
class PdfTaggedHeadingsTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void headingTaggedInBodyFontIsAHeading() throws IOException {
        // Typst API documentation: the parameter entries are H3 in the font of the text
        Path pdf = TestPdf.builder()
                .page()
                .taggedLine(720, "H1", "Documentation")
                .taggedLine(690, "H3", "lang str")
                .taggedLine(676, "P", "The primary language of the document.")
                .taggedLine(640, "H3", "title-long str or none")
                .taggedLine(626, "P", "Full thesis title on the cover page.")
                .writeTo(dir.resolve("typst.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                # Documentation

                ## lang str

                The primary language of the document.

                ## title-long str or none

                Full thesis title on the cover page.
                """);
    }

    @Test
    void sectionTaggedAsHeadingKeepsItsParagraphs() throws IOException {
        // NIST tags a whole section as H1: the element ends in the middle of a sentence
        Path pdf = TestPdf.builder()
                .page()
                .taggedLine(720, "H1", "the verifier passes on an assertion about the subscriber, who may be either known")
                .line(706, "or not, to the relying party.")
                .writeTo(dir.resolve("nist.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                the verifier passes on an assertion about the subscriber, who may be either known
                or not, to the relying party.
                """);
    }

    @Test
    void sentenceTaggedAsHeadingStaysText() throws IOException {
        // InDesign maps "_No_paragraph_style_" to H2: body text without a style is tagged as a heading
        Path pdf = TestPdf.builder()
                .page()
                .taggedLine(720, "P", "Some text before.")
                .taggedLine(690, "H2", "This line is ordinary text that the generator tagged as a heading.")
                .taggedLine(660, "P", "Some text after.")
                // other-c1: a long sentence without its full stop; NIST: a figure caption tagged H1
                .taggedLine(630, "H1", "This guidance explains how to complete an application under section 63G of the Act")
                .taggedLine(600, "P", "Some text after.")
                .taggedLine(570, "H1", "Figure 4-1 Digital Identity Model")
                .taggedLine(540, "P", "Some text after.")
                .writeTo(dir.resolve("indesign.pdf"));

        assertThat(converter.convert(pdf).markdown()).doesNotContain("#");
    }
}
