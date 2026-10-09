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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/**
 * Converts PDF to Markdown with Apache PDFBox.
 *
 * <p>Pipeline: {@link LineCollector} (text lines in reading order, from PDFBox) ->
 * {@link Figures} (text inside captioned figures removed) ->
 * {@link RuledTables} (captioned tables drawn as a grid of rules) ->
 * {@link TaggedTables} and {@link Lists} (list markers back on their lines) ->
 * {@link PageFurniture} (headers, footers, page numbers removed) -> {@link Paragraphs} -> {@link Headings}
 * -> {@link Hyphens} -> Markdown -> {@link Markdown#normalize}.
 *
 * <p>Current output: paragraphs separated by a blank line; a paragraph whose sentence goes on on the
 * next page stays whole. Headings: text larger than the body font, or bold text starting with a section
 * number; the level comes from the section number ({@code 2.1} is {@code ###}) or from the font size.
 * A PDF with images but no text (a scan) is rejected: OCR is not supported.
 * Bullet items become Markdown {@code - } items. Tables of tagged PDFs become Markdown tables;
 * in untagged PDFs (LaTeX, xml2rfc) a table drawn as a grid of rules with a caption ({@code Table 1.})
 * becomes one too, other table text stays ordinary text.
 * Text inside a figure is left out when the figure has a caption ({@code Figure 1.}); the caption stays.
 * Link annotations to web addresses become {@code [text](url)}. Block syntax at the start of a line
 * ({@code #}, {@code >}, code fences, rule lines) is escaped by {@link Markdown#escape}, so text such as {@code # layers}
 * in a table does not turn into a heading.
 *
 * <p>Stateless and thread-safe: every call works on its own document and collector.
 */
public final class PdfConverter implements DocumentConverter {

    /**
     * The end of a link at a line end and the next line starting with a link to the same address; group 2 is
     * that line's link text with its {@code ](target)}. Addresses with parentheses or in angle brackets are not
     * matched and stay two links.
     */
    private static final Pattern SPLIT_LINK = Pattern.compile(
            "\\]\\(([^)\\s<>]+)\\)[ \\t]*\\n[ \\t]*\\[((?:\\\\.|[^\\]\\\\\\n])*\\]\\(\\1\\))");

    /** Creates the converter; {@link java.util.ServiceLoader} calls it. */
    public PdfConverter() {
    }

    @Override
    public List<String> extensions() {
        return List.of("pdf");
    }

    /**
     * The file is opened here, not by {@code Loader.loadPDF(File)}: that closes it only on an
     * {@code IOException}, and damaged PDFs also fail with unchecked exceptions while loading.
     */
    @Override
    public String convert(Path path) {
        try (RandomAccessRead file = new RandomAccessReadBufferedFile(path.toFile())) {
            return convert(() -> Loader.loadPDF(file), path.toString());
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read PDF: " + path, e);
        }
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
            lines = Figures.remove(document, lines);
            List<String> tableMarkdown = new ArrayList<>();
            for (int table = 0; table < tables.size(); table++) {
                tableMarkdown.add(tables.markdown(table, collected.cellText()));
            }
            lines = RuledTables.replace(document, lines, tableMarkdown);
            lines = ScientificPowers.apply(document, lines);
            return Markdown.normalize(toMarkdown(lines, tableMarkdown, document.getNumberOfPages() > 0 && document.getPage(0).getRotation() % 360 == 0));
        } catch (InvalidPasswordException e) {
            throw new DocumentConversionException("PDF is encrypted: " + name, e);
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read PDF: " + name, e);
        } catch (DocumentConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            // damaged files make PDFBox fail with unchecked exceptions too: 30 of 750 randomly damaged
            // copies of real PDFs gave IllegalArgumentException ("illegal values" of a matrix) or NPE
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
        return toMarkdown(lines, tables, false);
    }

    static String toMarkdown(List<Line> lines, List<String> tables, boolean stampAllowed) {
        List<List<Line>> paragraphs = Paragraphs.group(PageFurniture.remove(Lists.attachMarkers(lines)));
        int[] levels = Headings.levels(paragraphs);
        Set<String> words = Hyphens.words(lines);

        float left = stampAllowed ? ArxivStamp.leftMargin(lines) : Float.NaN;
        StringJoiner out = new StringJoiner("\n\n");
        for (int i = 0; i < paragraphs.size(); i++) {
            List<Line> paragraph = paragraphs.get(i);
            ArxivStamp.Block stamp = stampAllowed ? ArxivStamp.at(paragraphs, levels, i, left) : null;
            if (stamp != null) {
                out.add(Markdown.escape(stamp.text()));
                i = stamp.end() - 1;
            } else if (paragraph.get(0).isTable()) {
                out.add(tables.get(paragraph.get(0).table()));
            } else if (levels[i] > 0) {
                out.add("#".repeat(levels[i]) + " " + Markdown.escape(Headings.text(paragraph)));
            } else {
                // links first: a word split inside a link broken over lines ends its line with "-](url)" until then
                String text = joinSplitLinks(paragraph.stream().map(line -> Lists.markdown(Markdown.escape(line.text())))
                        .collect(Collectors.joining("\n")));
                out.add(String.join("\n", Hyphens.join(text.lines().toList(), words)));
            }
        }
        return Markdown.escapeTagsOutsideLinks(out.toString());
    }

    /**
     * A link broken over two lines of a paragraph is one link: {@code [Distributed Computing](u)\n[Environment](u)}
     * becomes {@code [Distributed Computing\nEnvironment](u)}; Markdown allows a line break in the link text.
     */
    static String joinSplitLinks(String paragraph) {
        String joined = SPLIT_LINK.matcher(paragraph).replaceAll("\n$2");
        // a link over three lines leaves another pair; each pass joins a line, so this ends
        return joined.equals(paragraph) ? joined : joinSplitLinks(joined);
    }
}
