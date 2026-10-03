package io.github.yuraburyakov.casttomarkdown.pdf;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.UnsupportedFormatException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
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
 * {@link TaggedTables} and {@link Lists} (list markers back on their lines) ->
 * {@link PageFurniture} (headers, footers, page numbers removed) -> {@link Paragraphs} -> {@link Headings}
 * -> {@link Hyphens} -> Markdown -> {@link Markdown#normalize}.
 *
 * <p>Current output: paragraphs separated by a blank line; a paragraph whose sentence goes on on the
 * next page stays whole. Headings: text larger than the body font, or bold text starting with a section
 * number; the level comes from the section number ({@code 2.1} is {@code ###}) or from the font size.
 * A PDF with images but no text (a scan) is rejected: OCR is not supported.
 * Bullet items become Markdown {@code - } items. Tables of tagged PDFs become Markdown tables;
 * in untagged PDFs (LaTeX, WeasyPrint) table text stays ordinary text. Link annotations to web
 * addresses become {@code [text](url)}. Block syntax at the start of a line ({@code #}, {@code >},
 * code fences, rule lines) is escaped by {@link Markdown#escape}, so text such as {@code # layers}
 * in a table does not turn into a heading.
 *
 * <p>Stateless and thread-safe: every call works on its own document and collector.
 */
public final class PdfConverter implements DocumentConverter {

    /** Creates the converter; {@link java.util.ServiceLoader} calls it. */
    public PdfConverter() {
    }

    @Override
    public List<String> extensions() {
        return List.of("pdf");
    }

    @Override
    public String convert(Path path) {
        return convert(() -> Loader.loadPDF(path.toFile()), path.toString());
    }

    /**
     * ponytail: the whole stream is read into memory; PDFBox needs random access to the file.
     * A size limit comes with the configuration (builder).
     */
    @Override
    public String convert(InputStream input, String name) {
        return convert(() -> Loader.loadPDF(input.readAllBytes()), name);
    }

    private String convert(Source source, String name) {
        try (PDDocument document = source.load()) {
            TaggedTables tables = TaggedTables.read(document);
            LineCollector.Collected collected = LineCollector.collect(document, tables);
            List<Line> lines = collected.lines();
            if (lines.isEmpty() && hasImages(document)) {
                throw new UnsupportedFormatException("PDF has no text layer, only images (scanned document?); "
                        + "OCR is not supported, run OCR first: " + name);
            }
            List<String> tableMarkdown = new ArrayList<>();
            for (int table = 0; table < tables.size(); table++) {
                tableMarkdown.add(tables.markdown(table, collected.cellText()));
            }
            return Markdown.normalize(toMarkdown(lines, tableMarkdown));
        } catch (InvalidPasswordException e) {
            throw new DocumentConversionException("PDF is encrypted: " + name, e);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read PDF: " + name, e);
        }
    }

    /** Opens the document; the caller closes it. */
    @FunctionalInterface
    private interface Source {
        PDDocument load() throws IOException;
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
        return toMarkdown(lines, List.of());
    }

    /** As {@link #toMarkdown(List)}; a table placeholder line is replaced by {@code tables.get(line.table())}. */
    static String toMarkdown(List<Line> lines, List<String> tables) {
        List<List<Line>> paragraphs = Paragraphs.group(PageFurniture.remove(Lists.attachMarkers(lines)));
        int[] levels = Headings.levels(paragraphs);
        Set<String> words = Hyphens.words(lines);

        StringJoiner out = new StringJoiner("\n\n");
        for (int i = 0; i < paragraphs.size(); i++) {
            List<Line> paragraph = paragraphs.get(i);
            if (paragraph.get(0).isTable()) {
                out.add(tables.get(paragraph.get(0).table()));
            } else if (levels[i] > 0) {
                out.add("#".repeat(levels[i]) + " " + Markdown.escape(Headings.text(paragraph)));
            } else {
                List<String> texts = Hyphens.join(paragraph.stream().map(Line::text).toList(), words);
                out.add(texts.stream().map(text -> Lists.markdown(Markdown.escape(text))).collect(Collectors.joining("\n")));
            }
        }
        return out.toString();
    }
}
