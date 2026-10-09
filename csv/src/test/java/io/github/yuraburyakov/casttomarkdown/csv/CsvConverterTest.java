package io.github.yuraburyakov.casttomarkdown.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvConverterTest {

    private final CsvConverter converter = new CsvConverter();

    @TempDir
    Path dir;

    @Test
    void firstRowIsTheHeader() {
        assertThat(convert("name,age\nAnn,31\nBob,42\n", "a.csv"))
                .isEqualTo("| name | age |\n| --- | --- |\n| Ann | 31 |\n| Bob | 42 |\n");
    }

    @Test
    void quotedFieldsAfterRfc4180() {
        // commas, doubled quotes and line breaks inside quotes; CRLF line ends
        assertThat(convert("item,note\r\n\"a, b\",\"say \"\"hi\"\"\"\r\n\"multi\r\nline\",x|y\r\n", "a.csv"))
                .isEqualTo("| item | note |\n| --- | --- |\n| a, b | say \"hi\" |\n| multi line | x\\|y |\n");
    }

    @Test
    void spacesBeforeAnOpeningQuote() {
        // airtravel.csv (people.sc.fsu.edu): "Month", "1958", "1959"
        assertThat(convert("\"Month\", \"1958\", \"1959\"\n\"JAN\",  340,  360\n", "a.csv"))
                .isEqualTo("| Month | 1958 | 1959 |\n| --- | --- | --- |\n| JAN | 340 | 360 |\n");
    }

    @Test
    void semicolonAndTabSeparators() {
        // Excel in Europe writes semicolons; a .tsv has tabs
        assertThat(convert("Name;Preis\nKaffee;3,50\n", "a.csv")).isEqualTo("| Name | Preis |\n| --- | --- |\n| Kaffee | 3,50 |\n");
        assertThat(convert("a\tb\n1\t2\n", "a.tsv")).isEqualTo("| a | b |\n| --- | --- |\n| 1 | 2 |\n");
    }

    @Test
    void rowsOfOtherLengthsAndEmptyLines() {
        // the header takes the width of the widest row; a short row is not padded (a renderer adds empty cells)
        assertThat(convert("a,b\n1\n\n1,2,3\n", "a.csv"))
                .isEqualTo("| a | b |  |\n| --- | --- | --- |\n| 1 |\n| 1 | 2 | 3 |\n");
    }

    @Test
    void cellTextCannotBreakTheTable() {
        assertThat(convert("h\n# not a heading\n<b>tag</b>\n", "a.csv"))
                .isEqualTo("| h |\n| --- |\n| # not a heading |\n| \\<b>tag\\</b> |\n");
        // a backslash before a pipe would make the pipe end the cell in GFM
        assertThat(convert("h,i\nx\\|y,z\n", "a.csv")).isEqualTo("| h | i |\n| --- | --- |\n| x\\\\\\|y | z |\n");
    }

    @Test
    void emptyFileIsEmpty() {
        assertThat(convert("", "a.csv")).isEmpty();
        assertThat(convert("\n\n", "a.csv")).isEmpty();
    }

    @Test
    void hostileFileTakesLinearTimeAndDoesNotBlowUp() {
        String csv = ",".repeat(1_000_000) + "\n" + "x\n".repeat(200_000) + "\"never closed";

        String markdown = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> convert(csv, "a.csv"));

        assertThat(markdown.length()).isLessThan(10 * csv.length());
    }

    @Test
    void charsetsAndExtensions() throws IOException {
        Path file = dir.resolve("prices.CSV");
        Files.write(file, "Item;Price\nCafé;5 €\n".getBytes(Charset.forName("windows-1252")));
        assertThat(CastToMarkdown.create().convert(file).markdown()).isEqualTo("| Item | Price |\n| --- | --- |\n| Café | 5 € |\n");
        assertThat(CastToMarkdown.create().convert(new ByteArrayInputStream("a\tb".getBytes(StandardCharsets.UTF_8)), "x.tsv")
                .markdown()).isEqualTo("| a | b |\n| --- | --- |\n");
    }

    @Test
    void unreadableFileIsAConversionError() {
        assertThatThrownBy(() -> converter.convert(dir.resolve("missing.csv"))).isInstanceOf(DocumentConversionException.class);
    }

    private String convert(String csv, String name) {
        return converter.convert(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), name);
    }
}
