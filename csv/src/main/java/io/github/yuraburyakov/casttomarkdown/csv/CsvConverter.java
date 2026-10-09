package io.github.yuraburyakov.casttomarkdown.csv;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import io.github.yuraburyakov.casttomarkdown.internal.Text;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converts CSV and TSV files to a Markdown table; the first row is the header. Fields follow RFC 4180: quoted
 * fields may hold the separator, line breaks and doubled quotes. A {@code .tsv} file is separated by tabs; in a
 * {@code .csv} file the separator is the one of comma, semicolon (Excel in much of Europe) and tab that the first
 * row has most of. The charset comes from a byte order mark, else UTF-8, else windows-1252.
 *
 * <p>Stateless and thread-safe.
 */
public final class CsvConverter implements DocumentConverter {

    private static final char[] SEPARATORS = {',', ';', '\t'};
    private static final Pattern LINE_BREAK = Pattern.compile("\\s*\\R\\s*");
    /** The start of something CommonMark reads as raw HTML, also inside a table cell. */
    private static final Pattern TAG_START = Pattern.compile("<(?=[A-Za-z/!?])");

    /** Creates the converter; {@link java.util.ServiceLoader} calls it. */
    public CsvConverter() {
    }

    @Override
    public List<String> extensions() {
        return List.of("csv", "tsv");
    }

    @Override
    public String convert(Path path) {
        try {
            return render(Text.decode(Files.readAllBytes(path)), path.getFileName().toString());
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read CSV: " + path, e);
        }
    }

    @Override
    public String convert(InputStream input, String name) {
        try {
            return render(Text.decode(input.readAllBytes()), name);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read CSV: " + name, e);
        }
    }

    private static String render(String text, String name) {
        char separator = name.toLowerCase(Locale.ROOT).endsWith(".tsv") ? '\t' : separator(text);
        List<List<String>> rows = parse(text, separator);
        if (rows.isEmpty()) {
            return "";
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        List<String> header = new ArrayList<>(rows.get(0));
        while (header.size() < columns) {
            header.add("");
        }
        StringBuilder out = new StringBuilder();
        row(out, header);
        out.append('|').append(" --- |".repeat(columns)).append('\n');
        // rows are not padded to the header: a renderer adds the empty cells, and padding would blow up a file of
        // one very wide row and many short ones
        for (List<String> row : rows.subList(1, rows.size())) {
            row(out, row);
        }
        return Markdown.normalize(out.toString());
    }

    private static void row(StringBuilder out, List<String> cells) {
        out.append('|');
        for (String cell : cells) {
            String text = Markdown.tableCell(LINE_BREAK.matcher(cell).replaceAll(" ").strip());
            out.append(' ').append(TAG_START.matcher(text).replaceAll("\\\\<")).append(" |");
        }
        out.append('\n');
    }

    /** The separator of comma, semicolon and tab that the first record has most of outside quotes; comma for none. */
    private static char separator(String text) {
        int[] counts = new int[SEPARATORS.length];
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '\n' || c == '\r')) {
                break;
            } else if (!quoted) {
                for (int s = 0; s < SEPARATORS.length; s++) {
                    counts[s] += c == SEPARATORS[s] ? 1 : 0;
                }
            }
        }
        int best = 0;
        for (int s = 1; s < SEPARATORS.length; s++) {
            if (counts[s] > counts[best]) {
                best = s;
            }
        }
        return SEPARATORS[best];
    }

    /**
     * The records of the text (RFC 4180), each a list of fields; a blank line is no record. A quote that is never
     * closed takes the rest of the text into its field.
     */
    private static List<List<String>> parse(String text, char separator) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
                fieldStarted = true;
            } else if (c == separator) {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                endRow(rows, row, field, fieldStarted);
                row = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        endRow(rows, row, field, fieldStarted);
        return rows;
    }

    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder field, boolean fieldStarted) {
        if (fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
    }
}
