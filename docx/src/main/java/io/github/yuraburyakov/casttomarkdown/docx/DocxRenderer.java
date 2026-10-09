package io.github.yuraburyakov.casttomarkdown.docx;

import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;

/** Renders one DOCX document; a new instance for every document. */
final class DocxRenderer {

    private static final int MAX_HEADING_LEVEL = 6;
    /** Built-in heading style: name "heading 1" (the same in every Word language), id "Heading1". */
    /** A line break inside a paragraph (Shift+Enter) with the spaces around it. */
    private static final Pattern LINE_BREAKS = Pattern.compile("\\s*\\n\\s*");
    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)^heading\\s*(\\d)$");
    private static final String TITLE_STYLE = "title";
    private static final int NOT_STARTED = Integer.MIN_VALUE;
    /** Spaces per list nesting level: enough for both "- " and "10. " parents in CommonMark. */
    private static final String LIST_INDENT = "    ";

    private final XWPFDocument document;
    private final StringBuilder out = new StringBuilder();
    /**
     * Last item number per list definition ({@code abstractNumId}) and nesting level. By definition, not
     * by list instance ({@code numId}): instances of one definition go on numbering one list, unless an
     * instance restarts it with a start override.
     */
    private final Map<BigInteger, int[]> listCounters = new HashMap<>();
    /** List instances already seen: a start override applies to the first item of an instance. */
    private final Set<BigInteger> startedLists = new HashSet<>();
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
            } else if (element instanceof XWPFSDT sdt) {
                String text = sdt.getContent().getText().strip();
                if (!text.isEmpty()) {
                    // Preserve content, including TOCs; SDT does not imply disposable text.
                    block(escapeLines(text), false);
                }
            }
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
            block("#".repeat(level) + " " + Markdown.escape(LINE_BREAKS.matcher(text).replaceAll(" ")), false);
        } else if (paragraph.getNumID() != null && !"none".equals(paragraph.getNumFmt())) {
            listItem(paragraph, text);
        } else {
            block(escapeLines(text), false);
        }
    }

    /** The list definition ({@code abstractNumId}) of a list instance; the instance itself if it has none. */
    private BigInteger definitionOf(BigInteger numId) {
        XWPFNumbering numbering = document.getNumbering();
        BigInteger definition = numbering == null ? null : numbering.getAbstractNumID(numId);
        return definition != null ? definition : numId;
    }

    /**
     * The first number of a level ({@code w:start}), 1 when not set.
     * ponytail: a whole level redefined in a {@code w:lvlOverride} is not read, only its start override.
     */
    private int start(BigInteger definition, int level) {
        XWPFNumbering numbering = document.getNumbering();
        XWPFAbstractNum abstractNum = numbering == null ? null : numbering.getAbstractNum(definition);
        if (abstractNum != null) {
            for (CTLvl lvl : abstractNum.getCTAbstractNum().getLvlList()) {
                if (lvl.getIlvl() != null && lvl.getIlvl().intValue() == level && lvl.getStart() != null) {
                    return lvl.getStart().getVal().intValue();
                }
            }
        }
        return 1;
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
            BigInteger list = definitionOf(paragraph.getNumID());
            int[] counters = listCounters.computeIfAbsent(list, id -> {
                int[] fresh = new int[9];
                Arrays.fill(fresh, NOT_STARTED);
                return fresh;
            });
            BigInteger override = startedLists.add(paragraph.getNumID()) ? paragraph.getNumStartOverride() : null;
            if (override != null) {
                counters[level] = override.intValue() - 1;
            } else if (counters[level] == NOT_STARTED) {
                counters[level] = start(list, level) - 1;
            }
            counters[level]++;
            for (int deeper = level + 1; deeper < counters.length; deeper++) {
                counters[deeper] = NOT_STARTED;
            }
            marker = counters[level] + ".";
        }
        String indent = LIST_INDENT.repeat(level);
        String continuation = "\n" + indent + " ".repeat(marker.length() + 1);
        block(indent + marker + " " + escapeLines(text).replace("\n", continuation), true);
    }

    private void table(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        int columns = 0;
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                // paragraph by paragraph: XWPFTableCell.getText() glues them without a space, and skips links
                String text = cell.getParagraphs().stream()
                        .map(paragraph -> text(paragraph).strip())
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.joining(" "));
                cells.add(Markdown.tableCell(LINE_BREAKS.matcher(text).replaceAll(" ")));
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

    /**
     * Text of the runs; a footnote reference becomes {@code [^id]}, an external link {@code [text](url)}.
     * ponytail: links inside footnotes stay plain text, and so do HYPERLINK fields.
     */
    private String text(XWPFParagraph paragraph) {
        StringBuilder text = new StringBuilder();
        List<IRunElement> runs = paragraph.getIRuns();
        for (int i = 0; i < runs.size(); i++) {
            IRunElement element = runs.get(i);
            String url = element instanceof XWPFHyperlinkRun linkRun ? url(linkRun) : null;
            if (url != null) {
                // all runs of one link share its XML element
                CTHyperlink link = ((XWPFHyperlinkRun) element).getCTHyperlink();
                StringBuilder label = new StringBuilder();
                while (i < runs.size() && runs.get(i) instanceof XWPFHyperlinkRun run && run.getCTHyperlink() == link) {
                    label.append(run.text());
                    i++;
                }
                i--;
                text.append(Markdown.link(label.toString(), url));
            } else if (element instanceof XWPFRun run) {
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

    /** The target of an external link, or {@code null} for a link to a bookmark (Word's table of contents). */
    private String url(XWPFHyperlinkRun run) {
        XWPFHyperlink link = run.getHyperlink(document);
        return link == null ? null : link.getURL();
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
