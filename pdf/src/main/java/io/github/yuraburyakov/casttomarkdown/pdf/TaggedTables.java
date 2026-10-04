package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** Header cells in a row: none, some next to {@code TD}, or only {@code TH} (a header row). */
    private static final int NO_TH = 0;
    private static final int SOME_TH = 1;
    private static final int ONLY_TH = 2;

    /** Cell ids of each table, row by row. */
    private final List<List<List<Integer>>> tables = new ArrayList<>();
    /** Cell id of each piece of marked content (page and MCID, see {@link #key}). */
    private final Map<Long, Integer> cellByKey = new HashMap<>();
    /** Table index of each cell id. */
    private final List<Integer> tableByCell = new ArrayList<>();
    /**
     * Tables with header cells ({@code TH}) whose first row is not made of them: key-value tables such
     * as a Wikipedia infobox, with {@code TH} in the first column. Their first row is data, so Markdown
     * gets an empty header row. Tables without any {@code TH} keep their first row as the header:
     * generators often tag a visible header row as {@code TD}.
     */
    private final Set<Integer> withoutHeaderRow = new HashSet<>();
    /** Elements already walked: a hostile tree can share or loop back to elements. */
    private final Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Custom structure types of the document mapped to standard ones ({@code /RoleMap}). */
    private final Map<String, Object> roleMap;
    /**
     * Page number (from 1) by page dictionary. {@code PDPageTree.indexOf} walks the page tree on every
     * call, a quarter of the conversion time of a 1000-page PDF with 1000 tables. Keyed by the
     * dictionary: {@code getPage()} of a structure element returns a new {@code PDPage} every time.
     */
    private final Map<COSBase, Integer> pageNumbers = new IdentityHashMap<>();

    private TaggedTables(Map<String, Object> roleMap) {
        this.roleMap = roleMap;
    }

    static TaggedTables read(PDDocument document) {
        var root = document.getDocumentCatalog().getStructureTreeRoot();
        if (root == null) {
            return new TaggedTables(Map.of());
        }
        TaggedTables result = new TaggedTables(root.getRoleMap());
        int number = 1;
        for (PDPage page : document.getPages()) {
            result.pageNumbers.put(page.getCOSObject(), number++);
        }
        result.findTables(root, null, 0);
        return result;
    }

    /** Number of the page from 1, or 0 for a page that is not in the document. */
    private int pageNumber(PDPage page) {
        return pageNumbers.getOrDefault(page.getCOSObject(), 0);
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
     * The table as Markdown: the first row is the header (an empty one for a key-value table, see
     * {@link #withoutHeaderRow}), a cell is its text on one line with {@code |}
     * escaped, short rows are padded, columns that are empty in every row are left out.
     * ponytail: merged cells are not spread over the columns they span.
     */
    String markdown(int table, Map<Integer, StringBuilder> textByCell) {
        List<List<String>> rows = new ArrayList<>();
        for (List<Integer> row : tables.get(table)) {
            List<String> cells = new ArrayList<>();
            for (int cell : row) {
                StringBuilder text = textByCell.get(cell);
                cells.add(text == null ? "" : text.toString());
            }
            rows.add(cells);
        }
        return TableMarkdown.of(rows, withoutHeaderRow.contains(table));
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

    private void findTables(PDStructureNode node, PDPage inheritedPage, int depth) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement element && enter(element, depth)) {
                PDPage page = element.getPage() != null ? element.getPage() : inheritedPage;
                if ("Table".equals(type(element))) {
                    addTable(element, page, depth + 1);
                } else {
                    findTables(element, page, depth + 1);
                }
            }
        }
    }

    private void addTable(PDStructureElement table, PDPage page, int depth) {
        List<List<List<Long>>> rows = new ArrayList<>();
        List<Integer> headerCellsByRow = new ArrayList<>();
        collectRows(table, page, rows, headerCellsByRow, depth);
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
        if (headerCellsByRow.stream().anyMatch(kind -> kind != NO_TH) && headerCellsByRow.get(0) != ONLY_TH) {
            withoutHeaderRow.add(index);
        }
    }

    /** Rows can sit directly in the table or in {@code THead} / {@code TBody} / {@code TFoot}. */
    private void collectRows(PDStructureElement element, PDPage page, List<List<List<Long>>> rows,
            List<Integer> headerCellsByRow, int depth) {
        for (Object kid : element.getKids()) {
            if (kid instanceof PDStructureElement child && enter(child, depth)) {
                PDPage childPage = child.getPage() != null ? child.getPage() : page;
                if ("TR".equals(type(child))) {
                    List<List<Long>> cells = new ArrayList<>();
                    boolean th = false;
                    boolean td = false;
                    for (Object cell : child.getKids()) {
                        if (cell instanceof PDStructureElement cellElement && enter(cellElement, depth + 1)) {
                            th |= "TH".equals(type(cellElement));
                            td |= "TD".equals(type(cellElement));
                            List<Long> keys = new ArrayList<>();
                            PDPage cellPage = cellElement.getPage() != null ? cellElement.getPage() : childPage;
                            collectKeys(cellElement, cellPage, keys, depth + 2);
                            cells.add(keys);
                        }
                    }
                    rows.add(cells);
                    headerCellsByRow.add(!th ? NO_TH : td ? SOME_TH : ONLY_TH);
                } else {
                    collectRows(child, childPage, rows, headerCellsByRow, depth + 1);
                }
            }
        }
    }

    /** All marked content of the element and its descendants, in structure order. */
    private void collectKeys(PDStructureElement element, PDPage page, List<Long> keys,
            int depth) {
        for (Object kid : element.getKids()) {
            if (kid instanceof Integer mcid && page != null) {
                keys.add(key(pageNumber(page), mcid));
            } else if (kid instanceof PDMarkedContentReference reference) {
                PDPage referencePage = reference.getPage() != null ? reference.getPage() : page;
                if (referencePage != null) {
                    keys.add(key(pageNumber(referencePage), reference.getMCID()));
                }
            } else if (kid instanceof PDStructureElement child && enter(child, depth)) {
                collectKeys(child, child.getPage() != null ? child.getPage() : page, keys, depth + 1);
            }
        }
    }
}
