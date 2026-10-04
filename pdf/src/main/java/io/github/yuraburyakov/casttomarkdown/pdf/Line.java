package io.github.yuraburyakov.casttomarkdown.pdf;

/**
 * One text line as PDFBox emits it; {@code y} grows downwards from the top of the page,
 * {@code fontSize} is the size of most characters, {@code bold} means most characters are bold,
 * {@code rotated} means the text is not horizontal (e.g. vertical text in a page margin).
 * {@code table} is the index of a tagged table this line stands for (see {@link TaggedTables}),
 * or {@code -1} for ordinary text.
 * {@code x} and {@code y} are along the text direction; {@code pageX} and {@code pageY} are where the line
 * starts on the page (y downwards), the same as {@code x} and {@code y} for horizontal text.
 * {@code width} runs from the start of the first character to the end of the last one, 0 when unknown.
 */
record Line(int page, float pageHeight, float x, float y, float fontSize, boolean bold, boolean rotated, String text,
        int table, float width, float pageX, float pageY) {

    /** US Letter height, for lines built in tests. */
    private static final float DEFAULT_PAGE_HEIGHT = 792;

    Line(int page, float pageHeight, float x, float y, float fontSize, boolean bold, boolean rotated, String text,
            int table) {
        this(page, pageHeight, x, y, fontSize, bold, rotated, text, table, 0, x, y);
    }

    Line(int page, float pageHeight, float x, float y, float fontSize, boolean bold, boolean rotated, String text) {
        this(page, pageHeight, x, y, fontSize, bold, rotated, text, -1);
    }

    Line(int page, float x, float y, float fontSize, boolean bold, String text) {
        this(page, DEFAULT_PAGE_HEIGHT, x, y, fontSize, bold, false, text);
    }

    Line(int page, float x, float y, float fontSize, String text) {
        this(page, x, y, fontSize, false, text);
    }

    boolean isTable() {
        return table >= 0;
    }

    /** Font size rounded to 0.5 pt, so that tiny differences do not split a paragraph. */
    int sizeKey() {
        return Math.round(fontSize * 2);
    }

    /** Font size and boldness: bold sorts above regular text of the same size. */
    int styleKey() {
        return sizeKey() * 2 + (bold ? 1 : 0);
    }
}
