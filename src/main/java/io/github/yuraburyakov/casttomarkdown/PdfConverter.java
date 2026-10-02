package io.github.yuraburyakov.casttomarkdown;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Converts PDF to Markdown with Apache PDFBox.
 *
 * <p>Current output: plain text split into paragraphs. Paragraphs are detected by PDFBox
 * from vertical gaps between lines; pages are separated by a blank line.
 * Headings, lists and tables are not detected yet. Markdown special characters are not escaped yet.
 *
 * <p>Stateless and thread-safe: a new {@link PDFTextStripper} is created for each call.
 */
final class PdfConverter {

    String convert(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            stripper.setParagraphEnd("\n");
            stripper.setPageEnd("\n\n");
            return normalize(stripper.getText(document));
        } catch (InvalidPasswordException e) {
            throw new DocumentConversionException("PDF is encrypted: " + path, e);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read PDF: " + path, e);
        }
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
