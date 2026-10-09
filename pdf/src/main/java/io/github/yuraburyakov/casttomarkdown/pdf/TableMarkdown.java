package io.github.yuraburyakov.casttomarkdown.pdf;

import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** A table of cell texts as a Markdown table; shared by tagged tables and ruled tables. */
final class TableMarkdown {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TableMarkdown() {
    }

    /**
     * The first row is the header, or an empty header row is added when {@code emptyHeaderRow}; a cell is
     * its text on one line with {@code |} escaped, short rows are padded, columns that are empty in every row
     * are left out.
     */
    static String of(List<List<String>> table, boolean emptyHeaderRow) {
        List<List<String>> rows = new ArrayList<>();
        int columns = 0;
        for (List<String> row : table) {
            List<String> cells = new ArrayList<>();
            for (String text : row) {
                cells.add(Markdown.tableCell(WHITESPACE.matcher(text.strip()).replaceAll(" ")));
            }
            columns = Math.max(columns, cells.size());
            rows.add(cells);
        }
        for (List<String> cells : rows) {
            while (cells.size() < columns) {
                cells.add("");
            }
        }
        for (int column = columns - 1; column >= 0; column--) {
            int c = column;
            if (rows.stream().allMatch(cells -> cells.get(c).isEmpty())) {
                rows.forEach(cells -> cells.remove(c));
                columns--;
            }
        }
        if (emptyHeaderRow) {
            // the picture row of an infobox has no text
            rows.removeIf(cells -> cells.stream().allMatch(String::isEmpty));
            rows.add(0, Collections.nCopies(columns, ""));
        }
        StringBuilder markdown = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> cells = rows.get(r);
            markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
            if (r == 0) {
                markdown.append("|").append(" --- |".repeat(columns)).append('\n');
            }
        }
        return markdown.toString().stripTrailing();
    }
}
