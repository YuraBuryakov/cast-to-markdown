package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;

/**
 * Tables of a tagged PDF, read from its structure tree ({@code Table} / {@code TR} / {@code TH}, {@code TD}).
 * A cell is linked to its text on the page by marked-content ids (MCID): {@link LineCollector} collects
 * the text of every cell in reading order, and {@link #markdown} turns the cells into a Markdown table.
 *
 * <p>Untagged PDFs (LaTeX, WeasyPrint, old files) have no tables here; their table text stays ordinary text.
 */
final class TaggedTables {

    /** Smaller "tables" are mostly layout, not data; their text stays ordinary text. */
    private static final int MIN_ROWS = 2;
    private static final int MIN_COLUMNS = 2;
    /**
     * Structure trees of real documents are 10-20 levels deep. The limit keeps a hostile tree from
     * overflowing the stack; deeper content is not searched for tables.
     */
    private static final int MAX_DEPTH = 100;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Cell ids of each table, row by row. */
    private final List<List<List<Integer>>> tables = new ArrayList<>();
    /** Cell id of each piece of marked content (page and MCID, see {@link #key}). */
    private final Map<Long, Integer> cellByKey = new HashMap<>();
    /** Table index of each cell id. */
    private final List<Integer> tableByCell = new ArrayList<>();
    /** Elements already walked: a hostile tree can share or loop back to elements. */
    private final Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Custom structure types of the document mapped to standard ones ({@code /RoleMap}). */
    private final Map<String, Object> roleMap;

    private TaggedTables(Map<String, Object> roleMap) {
        this.roleMap = roleMap;
    }

    static TaggedTables read(PDDocument document) {
        var root = document.getDocumentCatalog().getStructureTreeRoot();
        if (root == null) {
            return new TaggedTables(Map.of());
        }
        TaggedTables result = new TaggedTables(root.getRoleMap());
        result.findTables(document, root, null, 0);
        return result;
    }

    /** Key of a piece of marked content: page number (from 1) and MCID. */
    static long key(int page, int mcid) {
        return ((long) page << 32) | (mcid & 0xFFFFFFFFL);
    }

    /** Id of the table cell the marked content belongs to, or {@code -1}. */
    int cellOf(long key) {
        return cellByKey.getOrDefault(key, -1);
    }

    int tableOfCell(int cell) {
        return tableByCell.get(cell);
    }

    boolean isEmpty() {
        return tables.isEmpty();
    }

    int size() {
        return tables.size();
    }

    /**
     * The table as Markdown: the first row is the header, a cell is its text on one line with {@code |}
     * escaped, short rows are padded, columns that are empty in every row are left out.
     * ponytail: merged cells are not spread over the columns they span.
     */
    String markdown(int table, Map<Integer, StringBuilder> textByCell) {
        List<List<String>> rows = new ArrayList<>();
        int columns = 0;
        for (List<Integer> row : tables.get(table)) {
            List<String> cells = new ArrayList<>();
            for (int cell : row) {
                StringBuilder text = textByCell.get(cell);
                cells.add(text == null ? "" : WHITESPACE.matcher(text.toString().strip()).replaceAll(" ").replace("|", "\\|"));
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

    /**
     * The standard structure type, one step through the role map as PDFBox does. Not
     * {@link PDStructureElement#getStandardStructureType()}: it climbs the parent links to the root
     * on every call, and loops forever when a hostile file makes them a cycle.
     */
    private String type(PDStructureElement element) {
        String type = element.getStructureType();
        return roleMap.get(type) instanceof String standard ? standard : type;
    }

    /** Whether to walk into the element: not too deep and not walked before. */
    private boolean enter(PDStructureElement element, int depth) {
        return depth < MAX_DEPTH && visited.add(element.getCOSObject());
    }

    private void findTables(PDDocument document, PDStructureNode node, PDPage inheritedPage, int depth) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement element && enter(element, depth)) {
                PDPage page = element.getPage() != null ? element.getPage() : inheritedPage;
                if ("Table".equals(type(element))) {
                    addTable(document, element, page, depth + 1);
                } else {
                    findTables(document, element, page, depth + 1);
                }
            }
        }
    }

    private void addTable(PDDocument document, PDStructureElement table, PDPage page, int depth) {
        List<List<List<Long>>> rows = new ArrayList<>();
        collectRows(document, table, page, rows, depth);
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (rows.size() < MIN_ROWS || columns < MIN_COLUMNS) {
            return;
        }
        int index = tables.size();
        List<List<Integer>> cellIds = new ArrayList<>();
        for (List<List<Long>> row : rows) {
            List<Integer> ids = new ArrayList<>();
            for (List<Long> cell : row) {
                int id = tableByCell.size();
                tableByCell.add(index);
                ids.add(id);
                for (Long key : cell) {
                    cellByKey.put(key, id);
                }
            }
            cellIds.add(ids);
        }
        tables.add(cellIds);
    }

    /** Rows can sit directly in the table or in {@code THead} / {@code TBody} / {@code TFoot}. */
    private void collectRows(PDDocument document, PDStructureElement element, PDPage page,
            List<List<List<Long>>> rows, int depth) {
        for (Object kid : element.getKids()) {
            if (kid instanceof PDStructureElement child && enter(child, depth)) {
                PDPage childPage = child.getPage() != null ? child.getPage() : page;
                if ("TR".equals(type(child))) {
                    List<List<Long>> cells = new ArrayList<>();
                    for (Object cell : child.getKids()) {
                        if (cell instanceof PDStructureElement cellElement && enter(cellElement, depth + 1)) {
                            List<Long> keys = new ArrayList<>();
                            PDPage cellPage = cellElement.getPage() != null ? cellElement.getPage() : childPage;
                            collectKeys(document, cellElement, cellPage, keys, depth + 2);
                            cells.add(keys);
                        }
                    }
                    rows.add(cells);
                } else {
                    collectRows(document, child, childPage, rows, depth + 1);
                }
            }
        }
    }

    /** All marked content of the element and its descendants, in structure order. */
    private void collectKeys(PDDocument document, PDStructureElement element, PDPage page, List<Long> keys,
            int depth) {
        for (Object kid : element.getKids()) {
            if (kid instanceof Integer mcid && page != null) {
                keys.add(key(document.getPages().indexOf(page) + 1, mcid));
            } else if (kid instanceof PDMarkedContentReference reference) {
                PDPage referencePage = reference.getPage() != null ? reference.getPage() : page;
                if (referencePage != null) {
                    keys.add(key(document.getPages().indexOf(referencePage) + 1, reference.getMCID()));
                }
            } else if (kid instanceof PDStructureElement child && enter(child, depth)) {
                collectKeys(document, child, child.getPage() != null ? child.getPage() : page, keys, depth + 1);
            }
        }
    }
}
