package io.github.yuraburyakov.casttomarkdown.xlsx;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.DocumentTooLargeException;
import io.github.yuraburyakov.casttomarkdown.internal.ConvertedDocument;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackageProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Converts XLSX workbooks to Markdown with Apache POI.
 *
 * <p>Output: each visible sheet with content as a Markdown table, its first row with content as the header; with
 * more than one such sheet, each table follows a heading with the sheet name. A cell is the text Excel shows for
 * it (number and date formats applied, US English), a formula the value Excel saved for it. Rows and columns empty
 * in the whole sheet are left out. Metadata: title, creator and language of the core properties.
 *
 * <p>Stateless and thread-safe: every call works on its own workbook.
 */
public final class XlsxConverter implements DocumentConverter {

    private static final Pattern LINE_BREAKS = Pattern.compile("\\s*\\R\\s*");
    /** A table of up to this many cells is written however few of them have text. */
    private static final long MAX_SPARSE_TABLE = 1_000_000;
    /** A larger table may have at most this many cells per cell with text. */
    private static final long SPARSE_FACTOR = 10;

    /** Creates the converter; {@link java.util.ServiceLoader} calls it. */
    public XlsxConverter() {
    }

    @Override
    public List<String> extensions() {
        return List.of("xlsx");
    }

    /** The file is read by POI with random access, opened read-only and released when the method returns. */
    @Override
    public ConvertedDocument convert(Path path) {
        return render(() -> open(path), path.toString());
    }

    @Override
    public ConvertedDocument convert(InputStream input, String name, URI source) {
        byte[] bytes;
        try {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read XLSX: " + name, e);
        }
        // POI may close the stream it gets, so it reads a copy, never the caller's stream
        return render(() -> new XSSFWorkbook(new ByteArrayInputStream(bytes)), name);
    }

    private static XSSFWorkbook open(Path path) throws IOException, InvalidFormatException {
        OPCPackage xlsx = OPCPackage.open(path.toFile(), PackageAccess.READ);
        try {
            return new XSSFWorkbook(xlsx);
        } catch (IOException | RuntimeException e) {
            xlsx.revert();
            throw e;
        }
    }

    /**
     * Renders the workbook with its metadata and closes it.
     * POI reports damaged files with many unchecked exception types, so all of them are wrapped.
     * ponytail: the whole workbook is in memory (XSSF user model), many times the file size for big sheets; the
     * streaming event API if that matters.
     */
    private static ConvertedDocument render(Source source, String name) {
        try (XSSFWorkbook workbook = source.open()) {
            PackageProperties properties = workbook.getPackage().getPackageProperties();
            return new ConvertedDocument(Markdown.normalize(Markdown.escapeTagsOutsideLinks(sheets(workbook))),
                    properties.getTitleProperty().orElse(null), properties.getCreatorProperty().orElse(null),
                    properties.getLanguageProperty().orElse(null));
        } catch (EncryptedDocumentException | OLE2NotOfficeXmlFileException e) {
            // a password-protected XLSX is an OLE2 container, like an old .xls renamed to .xlsx
            throw new DocumentConversionException(
                    "XLSX is password-protected, or is an old Excel .xls file, which is not supported: " + name, e);
        } catch (DocumentConversionException e) {
            throw e;
        } catch (IOException | InvalidFormatException | RuntimeException e) {
            throw new DocumentConversionException("Cannot read XLSX: " + name, e);
        }
    }

    private static String sheets(XSSFWorkbook workbook) {
        DataFormatter formatter = new DataFormatter(Locale.US);
        // the value Excel saved: evaluating formulas again would need the functions and links POI may not have
        formatter.setUseCachedValuesForFormulaCells(true);
        List<String> names = new ArrayList<>();
        List<String> tables = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (workbook.isSheetHidden(i) || workbook.isSheetVeryHidden(i)) {
                continue;
            }
            String table = table(workbook.getSheetAt(i), formatter);
            if (!table.isEmpty()) {
                names.add(workbook.getSheetName(i));
                tables.add(table);
            }
        }
        if (tables.size() == 1) {
            return tables.get(0);
        }
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            blocks.add("# " + Markdown.escape(names.get(i)));
            blocks.add(tables.get(i));
        }
        return String.join("\n\n", blocks);
    }

    /**
     * The sheet as a Markdown table, or {@code ""} when it has no text. Only the cells the file holds are read,
     * and only rows and columns with text become the table.
     *
     * @throws DocumentTooLargeException when the table would be many times larger than the text in it: a few cells
     *         far apart ({@code A1} and {@code XFD100000}) would fill rows times columns empty cells
     */
    private static String table(Sheet sheet, DataFormatter formatter) {
        List<Map<Integer, String>> rows = new ArrayList<>();
        SortedSet<Integer> columns = new TreeSet<>();
        long textCells = 0;
        for (Row row : sheet) {
            Map<Integer, String> cells = new HashMap<>();
            for (Cell cell : row) {
                String text = LINE_BREAKS.matcher(formatter.formatCellValue(cell)).replaceAll(" ").strip();
                if (!text.isEmpty()) {
                    cells.put(cell.getColumnIndex(), Markdown.tableCell(text));
                    columns.add(cell.getColumnIndex());
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
                textCells += cells.size();
            }
        }
        long tableCells = (long) rows.size() * columns.size();
        if (tableCells > MAX_SPARSE_TABLE && tableCells > SPARSE_FACTOR * textCells) {
            throw new DocumentTooLargeException("Sheet " + sheet.getSheetName() + " would be a table of " + rows.size()
                    + " rows and " + columns.size() + " columns for " + textCells + " cells with text");
        }
        // ponytail: merged cells are not spread over the columns they span, as in DOCX
        StringBuilder markdown = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            Map<Integer, String> cells = rows.get(r);
            markdown.append('|');
            for (int column : columns) {
                markdown.append(' ').append(cells.getOrDefault(column, "")).append(" |");
            }
            markdown.append('\n');
            if (r == 0) {
                markdown.append("|").append(" --- |".repeat(columns.size())).append('\n');
            }
        }
        return markdown.toString().stripTrailing();
    }

    @FunctionalInterface
    private interface Source {
        XSSFWorkbook open() throws IOException, InvalidFormatException;
    }
}
