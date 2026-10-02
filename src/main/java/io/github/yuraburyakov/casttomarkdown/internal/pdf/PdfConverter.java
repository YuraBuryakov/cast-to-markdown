package io.github.yuraburyakov.casttomarkdown.internal.pdf;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/**
 * Converts PDF to Markdown with Apache PDFBox.
 *
 * <p>Pipeline: {@link LineCollector} (text lines in reading order, from PDFBox) ->
 * {@link Paragraphs} -> {@link Headings} -> Markdown -> {@link #normalize}.
 *
 * <p>Current output: paragraphs separated by a blank line; headings: text larger than the body
 * font, or bold text starting with a section number; the level comes from the section number
 * ({@code 2.1} is {@code ###}) or from the font size. Pages are separated by a blank line.
 * Lists and tables are not detected yet. Markdown special characters are not escaped yet.
 *
 * <p>Stateless and thread-safe: every call works on its own document and collector.
 */
public final class PdfConverter implements DocumentConverter {

    @Override
    public String convert(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return normalize(toMarkdown(LineCollector.collect(document)));
        } catch (InvalidPasswordException e) {
            throw new DocumentConversionException("PDF is encrypted: " + path, e);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read PDF: " + path, e);
        }
    }

    /**
     * Groups lines into paragraphs and renders them: lines of a paragraph joined with {@code \n},
     * paragraphs separated by a blank line, headings as {@code #} lines.
     */
    static String toMarkdown(List<Line> lines) {
        List<List<Line>> paragraphs = Paragraphs.group(lines);
        int[] levels = Headings.levels(paragraphs);

        StringJoiner out = new StringJoiner("\n\n");
        for (int i = 0; i < paragraphs.size(); i++) {
            List<Line> paragraph = paragraphs.get(i);
            if (levels[i] > 0) {
                out.add("#".repeat(levels[i]) + " " + Headings.text(paragraph));
            } else {
                out.add(paragraph.stream().map(Line::text).collect(Collectors.joining("\n")));
            }
        }
        return out.toString();
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
