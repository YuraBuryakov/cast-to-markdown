package io.github.yuraburyakov.casttomarkdown.internal.pdf;

/**
 * One text line as PDFBox emits it; {@code y} grows downwards from the top of the page,
 * {@code fontSize} is the largest on the line, {@code bold} means most characters are bold,
 * {@code rotated} means the text is not horizontal (e.g. vertical text in a page margin).
 */
record Line(int page, float pageHeight, float x, float y, float fontSize, boolean bold, boolean rotated, String text) {

    /** US Letter height, for lines built in tests. */
    private static final float DEFAULT_PAGE_HEIGHT = 792;

    Line(int page, float x, float y, float fontSize, boolean bold, String text) {
        this(page, DEFAULT_PAGE_HEIGHT, x, y, fontSize, bold, false, text);
    }

    Line(int page, float x, float y, float fontSize, String text) {
        this(page, x, y, fontSize, false, text);
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
