package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tables of tagged PDFs (Word, InDesign, Chrome, LibreOffice write them) become Markdown tables. */
class PdfTablesTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void taggedTableBecomesMarkdownTableInPlace() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .line(720, "Table 6-1 Maximum Potential Impacts")
                .table(680,
                        new String[] {"Impact Categories", "Level 1", "Level 2"},
                        new String[] {"Financial loss", "Low", "Mod"},
                        new String[] {"Personal Safety", "N/A", "Low"})
                .line(560, "In analyzing risks, the agency SHALL consider all results.")
                .writeTo(dir.resolve("tables.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                Table 6-1 Maximum Potential Impacts

                | Impact Categories | Level 1 | Level 2 |
                | --- | --- | --- |
                | Financial loss | Low | Mod |
                | Personal Safety | N/A | Low |

                In analyzing risks, the agency SHALL consider all results.
                """);
    }

    @Test
    void tablesOnSeveralPages() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .table(700,
                        new String[] {"Name", "Value"},
                        new String[] {"Alpha", "1"})
                .page()
                .table(700,
                        new String[] {"Code", "Meaning"},
                        new String[] {"B2", "Second"})
                .writeTo(dir.resolve("two-pages.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                | Name | Value |
                | --- | --- |
                | Alpha | 1 |

                | Code | Meaning |
                | --- | --- |
                | B2 | Second |
                """);
    }

    @Test
    void untaggedTextOnARowLineDoesNotRepeatTheRow() throws IOException {
        // Chrome prints the address of a link inside a cell as untagged text on the same line
        Path pdf = TestPdf.builder()
                .page()
                .table(700,
                        new String[] {"Acronym", "UUID"},
                        new String[] {"Website", "RFC 9562"})
                .line(400, 684, "(https://www.rfc-editor.org)")
                .writeTo(dir.resolve("mixed-line.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                | Acronym | UUID |
                | --- | --- |
                | Website | RFC 9562 |

                (https://www.rfc-editor.org)
                """);
    }

    @Test
    void dropsEmptyColumnsAndEscapesPipes() throws IOException {
        // NIST (Word): every row starts with an empty cell.
        Path pdf = TestPdf.builder()
                .page()
                .table(700,
                        new String[] {"", "Section Name", "Normative/Informative"},
                        new String[] {"", "1. Purpose", "Informative | see 1.1"})
                .writeTo(dir.resolve("empty-column.pdf"));

        assertThat(converter.convert(pdf).markdown()).isEqualTo("""
                | Section Name | Normative/Informative |
                | --- | --- |
                | 1. Purpose | Informative \\| see 1.1 |
                """);
    }

    @Test
    void singleRowLayoutTableStaysText() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .table(700, new String[] {"Left column text", "Right column text"})
                .writeTo(dir.resolve("layout.pdf"));

        assertThat(converter.convert(pdf).markdown()).doesNotContain("|").contains("Left column text");
    }

    @Test
    void untaggedTableTextStaysText() throws IOException {
        Path pdf = TestPdf.builder()
                .page()
                .line(700, "Impact Categories  Level 1  Level 2")
                .line(684, "Financial loss  Low  Mod")
                .writeTo(dir.resolve("untagged.pdf"));

        assertThat(converter.convert(pdf).markdown()).doesNotContain("|");
    }
}
