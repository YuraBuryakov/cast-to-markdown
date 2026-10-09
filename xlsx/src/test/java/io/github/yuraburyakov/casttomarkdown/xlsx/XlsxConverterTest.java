package io.github.yuraburyakov.casttomarkdown.xlsx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.PreparedDocument;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XlsxConverterTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void oneSheetIsATableWithShownValues() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sales");
        row(sheet, 0, "Item", "Price", "Date");
        Row tea = row(sheet, 1, "Tea");
        tea.createCell(1).setCellValue(1234.5);
        CellStyle money = workbook.createCellStyle();
        money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        tea.getCell(1).setCellStyle(money);
        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        tea.createCell(2).setCellValue(LocalDate.of(2026, 10, 9));
        tea.getCell(2).setCellStyle(date);
        Row total = row(sheet, 3, "Total");
        total.createCell(1).setCellFormula("B2*2");
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

        // the empty row 3 is left out; a formula shows the value Excel saved
        assertThat(convert(workbook)).isEqualTo("| Item | Price | Date |\n| --- | --- | --- |\n"
                + "| Tea | 1,234.50 | 2026-10-09 |\n| Total | 2469 |  |\n");
    }

    @Test
    void severalSheetsGetTheirNamesAsHeadingsAndHiddenOrEmptyOnesAreLeftOut() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        row(workbook.createSheet("Q1"), 0, "a");
        workbook.createSheet("Empty");
        row(workbook.createSheet("Secret"), 0, "hidden");
        workbook.setSheetHidden(2, true);
        row(workbook.createSheet("# Q2"), 0, "b");

        assertThat(convert(workbook)).isEqualTo("# Q1\n\n| a |\n| --- |\n\n# \\# Q2\n\n| b |\n| --- |\n");
    }

    @Test
    void cellTextCannotBreakTheTable() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("S");
        // empty first column and a gap column: both left out
        Row row = sheet.createRow(0);
        row.createCell(1).setCellValue("a|b");
        row.createCell(3).setCellValue("two\nlines <b>bold</b>, x<5");

        assertThat(convert(workbook)).isEqualTo("| a\\|b | two lines \\<b>bold\\</b>, x<5 |\n| --- | --- |\n");
    }

    @Test
    void metadataComesFromTheCoreProperties() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        row(workbook.createSheet("S"), 0, "a");
        workbook.getProperties().getCoreProperties().setTitle("Budget 2026");
        workbook.getProperties().getCoreProperties().setCreator("Ada");

        PreparedDocument document = converter.convert(new ByteArrayInputStream(bytes(workbook)), "b.xlsx");
        assertThat(document.title()).contains("Budget 2026");
        assertThat(document.author()).contains("Ada");
        assertThat(document.language()).isEmpty();
    }

    @Test
    void fileAndStreamGiveTheSameAndTheStreamIsNotClosed() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        row(workbook.createSheet("S"), 0, "a");
        byte[] xlsx = bytes(workbook);
        Path file = Files.write(dir.resolve("book.XLSX"), xlsx);

        boolean[] closed = {false};
        InputStream stream = new ByteArrayInputStream(xlsx) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        assertThat(converter.convert(stream, "book.xlsx").markdown()).isEqualTo(converter.convert(file).markdown())
                .isEqualTo("| a |\n| --- |\n");
        assertThat(closed[0]).isFalse();
    }

    @Test
    void oldXlsAndDamagedFilesAreConversionErrors() throws IOException {
        HSSFWorkbook xls = new HSSFWorkbook();
        xls.createSheet("S");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        xls.write(out);
        xls.close();

        assertThatThrownBy(() -> converter.convert(new ByteArrayInputStream(out.toByteArray()), "old.xlsx"))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("old Excel .xls");
        assertThatThrownBy(() -> converter.convert(
                new ByteArrayInputStream("not a workbook".getBytes(StandardCharsets.UTF_8)), "bad.xlsx"))
                .isInstanceOf(DocumentConversionException.class);
        assertThatThrownBy(() -> converter.convert(dir.resolve("missing.xlsx")))
                .isInstanceOf(DocumentConversionException.class);
    }

    private static Row row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int c = 0; c < values.length; c++) {
            row.createCell(c).setCellValue(values[c]);
        }
        return row;
    }

    private String convert(Workbook workbook) throws IOException {
        return converter.convert(new ByteArrayInputStream(bytes(workbook)), "book.xlsx").markdown();
    }

    private static byte[] bytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }
}
