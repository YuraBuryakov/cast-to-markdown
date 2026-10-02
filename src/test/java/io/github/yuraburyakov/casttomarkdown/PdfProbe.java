package io.github.yuraburyakov.casttomarkdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Research tool, not a test: shows what PDFBox returns for real PDFs.
 *
 * <p>For every {@code *.pdf} in the input directory it writes:
 * <ul>
 *   <li>{@code <name>.current.md} - output of the library as it is now;</li>
 *   <li>{@code <name>.sorted.md} - the same text with {@code sortByPosition=true}, to compare reading order;</li>
 *   <li>{@code <name>.fonts.txt} - one row per {@code writeString} call on the first pages:
 *       page, y, glyph height reported by PDFBox, dominant font and size, text.</li>
 * </ul>
 * and prints page count, tagged/untagged and producer for each file.
 *
 * <p>Run after {@code mvn test-compile}:
 * <pre>{@code
 * mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/test-classes;target/classes;<contents of target/cp.txt>" \
 *     io.github.yuraburyakov.casttomarkdown.PdfProbe <pdf-dir> <out-dir>
 * }</pre>
 * (use {@code :} instead of {@code ;} as the path separator outside Windows).
 *
 * <p>Findings from the first run: Obsidian note "CastToMarkdown - Experiment 01 PDFBox on Real PDFs".
 */
public final class PdfProbe {

    private static final int FONT_DUMP_PAGES = 4;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: PdfProbe <pdf-dir> <out-dir>");
        }
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        List<Path> pdfs;
        try (var files = Files.list(Path.of(args[0]))) {
            pdfs = files.filter(p -> p.toString().endsWith(".pdf")).sorted().toList();
        }
        for (Path pdf : pdfs) {
            String name = pdf.getFileName().toString().replaceFirst("\\.pdf$", "");
            String markdown;
            try {
                markdown = CastToMarkdown.create().convert(pdf).markdown();
            } catch (DocumentConversionException e) {
                markdown = "CONVERSION FAILED: " + e.getMessage() + "\n";
                System.out.println(name + ": " + markdown.strip());
            }
            Files.writeString(out.resolve(name + ".current.md"), markdown);
            try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                System.out.printf("%s: pages=%d tagged=%s producer=%s creator=%s%n",
                        name,
                        document.getNumberOfPages(),
                        document.getDocumentCatalog().getStructureTreeRoot() != null,
                        document.getDocumentInformation().getProducer(),
                        document.getDocumentInformation().getCreator());
                Files.writeString(out.resolve(name + ".sorted.md"), sortedText(document));
                Files.writeString(out.resolve(name + ".fonts.txt"), fontDump(document));
            }
        }
    }

    private static String sortedText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");
        stripper.setParagraphEnd("\n");
        stripper.setPageEnd("\n\n");
        return stripper.getText(document);
    }

    private static String fontDump(PDDocument document) throws IOException {
        StringBuilder dump = new StringBuilder();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) throws IOException {
                if (!positions.isEmpty()) {
                    Map<String, Long> fonts = positions.stream().collect(Collectors.groupingBy(
                            p -> p.getFont().getName() + "@" + Math.round(p.getFontSizeInPt() * 10) / 10.0,
                            Collectors.counting()));
                    String dominant = Collections.max(fonts.entrySet(), Map.Entry.comparingByValue()).getKey();
                    TextPosition first = positions.get(0);
                    dump.append(String.format("p%d y=%6.1f h=%5.2f %-40s | %s%n",
                            getCurrentPageNo(), first.getYDirAdj(), first.getHeightDir(), dominant, text));
                }
                super.writeString(text, positions);
            }
        };
        stripper.setEndPage(FONT_DUMP_PAGES);
        stripper.getText(document);
        return dump.toString();
    }
}
