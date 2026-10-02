package io.github.yuraburyakov.casttomarkdown.internal.pdf;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.UnsupportedFormatException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/**
 * Converts PDF to Markdown with Apache PDFBox.
 *
 * <p>Pipeline: {@link LineCollector} (text lines in reading order, from PDFBox) ->
 * {@link PageFurniture} (headers, footers, page numbers removed) -> {@link Paragraphs} -> {@link Headings}
 * -> Markdown -> {@link #normalize}.
 *
 * <p>Current output: paragraphs separated by a blank line; headings: text larger than the body
 * font, or bold text starting with a section number; the level comes from the section number
 * ({@code 2.1} is {@code ###}) or from the font size. Pages are separated by a blank line.
 * A PDF with images but no text (a scan) is rejected: OCR is not supported.
 * Lists and tables are not detected yet. Of Markdown special characters only {@code #} at the start
 * of a line is escaped, so text such as {@code # layers} in a table does not turn into a heading.
 *
 * <p>Stateless and thread-safe: every call works on its own document and collector.
 */
public final class PdfConverter implements DocumentConverter {

    private static final Pattern LEADING_HASH = Pattern.compile("^(\\s*)#");

    @Override
    public String convert(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            List<Line> lines = LineCollector.collect(document);
            if (lines.isEmpty() && hasImages(document)) {
                throw new UnsupportedFormatException("PDF has no text layer, only images (scanned document?); "
                        + "OCR is not supported, run OCR first: " + path);
            }
            return normalize(toMarkdown(lines));
        } catch (InvalidPasswordException e) {
            throw new DocumentConversionException("PDF is encrypted: " + path, e);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read PDF: " + path, e);
        }
    }

    /**
     * Whether any page draws an image. A PDF without text but with images is most likely scanned;
     * one without either is just empty.
     * ponytail: only images placed directly on the page are seen, not images inside form XObjects.
     */
    private static boolean hasImages(PDDocument document) throws IOException {
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Groups lines into paragraphs and renders them: lines of a paragraph joined with {@code \n},
     * paragraphs separated by a blank line, headings as {@code #} lines.
     */
    static String toMarkdown(List<Line> lines) {
        List<List<Line>> paragraphs = Paragraphs.group(PageFurniture.remove(lines));
        int[] levels = Headings.levels(paragraphs);

        StringJoiner out = new StringJoiner("\n\n");
        for (int i = 0; i < paragraphs.size(); i++) {
            List<Line> paragraph = paragraphs.get(i);
            if (levels[i] > 0) {
                out.add("#".repeat(levels[i]) + " " + escape(Headings.text(paragraph)));
            } else {
                out.add(paragraph.stream().map(line -> escape(line.text())).collect(Collectors.joining("\n")));
            }
        }
        return out.toString();
    }

    /**
     * Escapes {@code #} at the start of a line, which Markdown reads as a heading.
     * ponytail: other block markers ({@code >}, {@code -}, {@code *}, code fences) are not escaped yet (Q-API-02).
     */
    static String escape(String line) {
        return LEADING_HASH.matcher(line).replaceFirst("$1\\\\#");
    }

    /**
     * Unifies line endings, removes trailing spaces, collapses runs of blank lines into one
     * and makes a non-empty result end with a single {@code \n}.
     */
    static String normalize(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        StringBuilder out = new StringBuilder(text.length());
        boolean blankLineBefore = false;
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (trimmed.isEmpty()) {
                blankLineBefore = out.length() > 0;
                continue;
            }
            if (out.length() > 0) {
                out.append(blankLineBefore ? "\n\n" : "\n");
            }
            out.append(trimmed);
            blankLineBefore = false;
        }
        return out.length() == 0 ? "" : out.append('\n').toString();
    }
}
