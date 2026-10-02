package io.github.yuraburyakov.casttomarkdown.docx;

import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/** Renders one DOCX document; a new instance for every document. */
final class DocxRenderer {

    private static final int MAX_HEADING_LEVEL = 6;
    /** Built-in heading style: name "heading 1" (the same in every Word language), id "Heading1". */
    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)^heading\\s*(\\d)$");
    private static final String TITLE_STYLE = "title";
    /** Spaces per list nesting level: enough for both "- " and "10. " parents in CommonMark. */
    private static final String LIST_INDENT = "    ";

    private final XWPFDocument document;
    private final StringBuilder out = new StringBuilder();
    /** Current item number per list ({@code numId}) and nesting level. */
    private final Map<BigInteger, int[]> listCounters = new HashMap<>();
    /** Footnote ids in order of first reference. */
    private final List<BigInteger> footnotes = new ArrayList<>();
    private boolean lastWasListItem;

    DocxRenderer(XWPFDocument document) {
        this.document = document;
    }

    String render() {
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                paragraph(paragraph);
            } else if (element instanceof XWPFTable table) {
                table(table);
            }
            // ponytail: content controls (XWPFSDT) at body level are skipped; Word puts its table of contents there.
        }
        footnoteDefinitions();
        return out.toString();
    }

    private void paragraph(XWPFParagraph paragraph) {
        String text = text(paragraph).strip();
        if (text.isEmpty()) {
            return;
        }
        int level = headingLevel(paragraph);
        if (level > 0) {
            block("#".repeat(level) + " " + Markdown.escape(text.replaceAll("\\s*\\n\\s*", " ")), false);
        } else if (paragraph.getNumID() != null && !"none".equals(paragraph.getNumFmt())) {
            listItem(paragraph, text);
        } else {
            block(escapeLines(text), false);
        }
    }

    private void listItem(XWPFParagraph paragraph, String text) {
        BigInteger ilvl = paragraph.getNumIlvl();
        int level = ilvl == null ? 0 : Math.min(ilvl.intValue(), 8);
        String marker;
        if ("bullet".equals(paragraph.getNumFmt())) {
            marker = "-";
        } else {
            // Markdown numbers a list from its first number, so the document's own numbering is kept
            // even when the list continues after a heading.
            int[] counters = listCounters.computeIfAbsent(paragraph.getNumID(), id -> new int[9]);
            counters[level]++;
            for (int deeper = level + 1; deeper < counters.length; deeper++) {
                counters[deeper] = 0;
            }
            marker = counters[level] + ".";
        }
        String indent = LIST_INDENT.repeat(level);
        String continuation = "\n" + indent + " ".repeat(marker.length() + 1);
        block(indent + marker + " " + text.replace("\n", continuation), true);
    }

    private void table(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        int columns = 0;
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().strip().replaceAll("\\s*\\n\\s*", " ").replace("|", "\\|"));
            }
            columns = Math.max(columns, cells.size());
            rows.add(cells);
        }
        if (columns == 0) {
            return;
        }
        // ponytail: merged cells are not spread over the columns they span; short rows are padded at the end.
        StringBuilder markdown = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> cells = rows.get(r);
            while (cells.size() < columns) {
                cells.add("");
            }
            markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
            if (r == 0) {
                markdown.append("|").append(" --- |".repeat(columns)).append('\n');
            }
        }
        block(markdown.toString().stripTrailing(), false);
    }

    /** Text of the runs; a footnote reference becomes {@code [^id]}. */
    private String text(XWPFParagraph paragraph) {
        StringBuilder text = new StringBuilder();
        for (IRunElement element : paragraph.getIRuns()) {
            if (element instanceof XWPFRun run) {
                if (run.getCTR().sizeOfFootnoteReferenceArray() > 0) {
                    BigInteger id = run.getCTR().getFootnoteReferenceArray(0).getId();
                    if (!footnotes.contains(id)) {
                        footnotes.add(id);
                    }
                    text.append("[^").append(id).append(']');
                } else {
                    text.append(run.text());
                }
            } else if (element instanceof XWPFSDT sdt) {
                text.append(sdt.getContent().getText());
            }
        }
        return text.toString();
    }

    /** {@code 1} for Title and Heading 1, {@code 2} for Heading 2, ...; {@code 0} for other styles. */
    private int headingLevel(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null) {
            return 0;
        }
        XWPFStyle style = document.getStyles() == null ? null : document.getStyles().getStyle(styleId);
        String name = style == null || style.getName() == null ? styleId : style.getName();
        if (name.toLowerCase(Locale.ROOT).equals(TITLE_STYLE)) {
            return 1;
        }
        Matcher heading = HEADING_STYLE.matcher(name);
        if (!heading.matches()) {
            heading = HEADING_STYLE.matcher(styleId);
        }
        return heading.matches() ? Math.min(MAX_HEADING_LEVEL, Math.max(1, Integer.parseInt(heading.group(1)))) : 0;
    }

    private void footnoteDefinitions() {
        for (BigInteger id : footnotes) {
            XWPFFootnote footnote = document.getFootnoteByID(id.intValue());
            if (footnote == null) {
                continue;
            }
            String text = footnote.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().strip())
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.joining(" "));
            block("[^" + id + "]: " + text, false);
        }
    }

    /** Adds a block: list items follow each other on the next line, other blocks after a blank line. */
    private void block(String markdown, boolean listItem) {
        if (out.length() > 0) {
            out.append(listItem && lastWasListItem ? "\n" : "\n\n");
        }
        out.append(markdown);
        lastWasListItem = listItem;
    }

    private static String escapeLines(String text) {
        return text.lines().map(Markdown::escape).collect(Collectors.joining("\n"));
    }
}
