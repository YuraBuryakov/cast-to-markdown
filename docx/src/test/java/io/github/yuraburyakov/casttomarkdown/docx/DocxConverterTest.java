package io.github.yuraburyakov.casttomarkdown.docx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.PreparedDocument;
import io.github.yuraburyakov.casttomarkdown.UnsupportedFormatException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Through the public API, so the ServiceLoader registration of the module is tested too. */
class DocxConverterTest {

    @TempDir
    Path dir;

    private final CastToMarkdown converter = CastToMarkdown.create();

    @Test
    void bodySdtTextStaysInOrderAndInlineSdtIsNotDuplicated() throws IOException {
        byte[] docx = TestDocx.builder().paragraph("Before")
                .bodySdt("First content paragraph", "Second content paragraph")
                .inlineSdt("Inline content").paragraph("After").bytes();
        assertThat(converter.convert(new ByteArrayInputStream(docx), "content.docx").markdown())
                .isEqualTo("Before\n\nFirst content paragraph\nSecond content paragraph\n\nInline content\n\nAfter\n");
    }

    @Test
    void emptyBodySdtDoesNotAddBlocks() throws IOException {
        byte[] docx = TestDocx.builder().paragraph("Before").bodySdt().bodySdt("   ")
                .paragraph("After").bytes();
        assertThat(converter.convert(new ByteArrayInputStream(docx), "empty.docx").markdown())
                .isEqualTo("Before\n\nAfter\n");
    }

    @Test
    void bodySdtUsesExistingBlockEscapingPolicyForEveryLine() throws IOException {
        // Inline syntax and list markers intentionally retain the existing Markdown policy/limitations.
        byte[] docx = TestDocx.builder().bodySdt("# literal", "> literal", "```", "---", "*inline*", "1. literal").bytes();
        assertThat(converter.convert(new ByteArrayInputStream(docx), "syntax.docx").markdown())
                .isEqualTo("\\# literal\n\\> literal\n\\```\n\\---\n*inline*\n1. literal\n");
    }

    @Test
    void bodySdtSeparatesSurroundingListItems() throws IOException {
        byte[] docx = TestDocx.builder().bullet(0, "Before").bodySdt("Content")
                .bullet(0, "After").bytes();
        assertThat(converter.convert(new ByteArrayInputStream(docx), "list.docx").markdown())
                .isEqualTo("- Before\n\nContent\n\n- After\n");
    }

    @Test
    void headingsComeFromParagraphStyles() throws IOException {
        byte[] docx = TestDocx.builder()
                .styled("Title", "Guidance")
                .paragraph("Intro text.")
                .styled("Heading2", "Who can apply")
                .paragraph("Body text.")
                .bytes();

        assertThat(convert(docx)).isEqualTo("# Guidance\n\nIntro text.\n\n## Who can apply\n\nBody text.\n");
    }

    @Test
    void boldParagraphWithoutAHeadingStyleStaysAParagraph() throws IOException {
        // decided 2026-10-09: a false heading cuts a document for RAG in the wrong place; court forms use bold text
        // for labels, glossaries for terms
        byte[] docx = TestDocx.builder()
                .boldParagraph("Applicant details")
                .paragraph("Body text.")
                .bytes();

        assertThat(convert(docx)).isEqualTo("Applicant details\n\nBody text.\n");
    }

    @Test
    void skipsEmptyParagraphsAndEscapesHashAtLineStart() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraph("First.")
                .paragraph("")
                .paragraph("   ")
                .paragraph("# not a heading")
                .bytes();

        assertThat(convert(docx)).isEqualTo("First.\n\n\\# not a heading\n");
    }

    @Test
    void escapesBlockSyntaxInParagraphsAndListItems() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraph("```not code")
                .paragraph("> not a quote")
                .paragraph("***")
                .bullet(0, "# not a heading inside the item")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                \\```not code

                \\> not a quote

                \\***

                - \\# not a heading inside the item
                """);
    }

    @Test
    void bulletListWithNesting() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraph("Licensing authorities should:")
                .bullet(0, "apply checks consistently")
                .bullet(1, "to all applicants")
                .bullet(0, "keep records")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                Licensing authorities should:

                - apply checks consistently
                    - to all applicants
                - keep records
                """);
    }

    @Test
    void numberedListKeepsDocumentNumberingAfterHeading() throws IOException {
        // A court order (gov.uk BP15): one numbered list runs through all sections.
        byte[] docx = TestDocx.builder()
                .numbered(0, "An account shall be taken.")
                .numbered(0, "X shall produce an account.")
                .styled("Heading2", "Objections")
                .numbered(0, "Objections shall be made in writing.")
                .numbered(1, "with reasons")
                .numbered(0, "Responses shall be filed.")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                1. An account shall be taken.
                2. X shall produce an account.

                ## Objections

                3. Objections shall be made in writing.
                    1. with reasons
                4. Responses shall be filed.
                """);
    }

    @Test
    void numberingFollowsStartValuesOverridesAndSharedDefinitions() throws IOException {
        byte[] docx = TestDocx.builder()
                .newNumberedList(5)
                .numbered(0, "Starts at five")
                .numbered(1, "nested starts at five too")
                .numbered(0, "Six")
                .paragraph("Text between.")
                .newNumberedInstance(null)
                .numbered(0, "Another w:num of the same list goes on")
                .paragraph("Restart numbering:")
                .newNumberedInstance(3)
                .numbered(0, "Override starts at three")
                .numbered(0, "Four")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                5. Starts at five
                    5. nested starts at five too
                6. Six

                Text between.

                7. Another w:num of the same list goes on

                Restart numbering:

                3. Override starts at three
                4. Four
                """);
    }

    @Test
    void tableBecomesMarkdownTable() throws IOException {
        byte[] docx = TestDocx.builder()
                .table(new String[] {"Level", "Impact"},
                        new String[] {"IAL1", "Low | Mod"},
                        new String[] {"IAL2", "High"})
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                | Level | Impact |
                | --- | --- |
                | IAL1 | Low \\| Mod |
                | IAL2 | High |
                """);
    }

    @Test
    void cellParagraphsAreSeparatedAndCellLinksKept() throws IOException {
        // POI's XWPFTableCell.getText() glues paragraphs: "Low riskSee" and drops the link
        byte[] docx = TestDocx.builder()
                .tableWithTwoParagraphCell("Notes", "Low risk.", "See ", "the guide", "https://example.org/guide")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                | Notes |
                | --- |
                | Low risk. See [the guide](https://example.org/guide) |
                """);
    }

    @Test
    void footnotesBecomeMarkdownFootnotes() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraphWithFootnote("Order for an account", "Often made with directions to be given later.")
                .paragraph("Next paragraph.")
                .bytes();

        String markdown = convert(docx);

        assertThat(markdown).startsWith("Order for an account[^");
        assertThat(markdown).containsPattern("\\[\\^(\\d+)][\\s\\S]*\\n\\[\\^\\1]: Often made with directions to be given later\\.\\n$");
    }

    @Test
    void externalLinkBecomesMarkdownLink() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraphWithLink("See ", "https://example.org/guide", " for details.", "the guide")
                .bytes();

        assertThat(convert(docx)).isEqualTo("See [the guide](https://example.org/guide) for details.\n");
    }

    @Test
    void linkSplitOverRunsIsOneLink() throws IOException {
        // Word starts a new run inside a link when the formatting changes; a trailing space stays outside
        byte[] docx = TestDocx.builder()
                .paragraphWithLink("Read ", "https://example.org/", "now.", "the ", "full ", "text ")
                .bytes();

        assertThat(convert(docx)).isEqualTo("Read [the full text](https://example.org/) now.\n");
    }

    @Test
    void tagsInTextAreEscapedAndLinksStay() throws IOException {
        // "List<" and "E" are in different runs; a link to a bookmark leaves its runs as plain text
        byte[] docx = TestDocx.builder()
                .paragraph("Use <b> for bold, a < b.")
                .paragraphWithLink("Returns List<", null, "> items.", "E")
                .paragraphWithLink("See ", "https://example.org/a(b)", ".", "Set<T>")
                .bytes();

        assertThat(convert(docx)).isEqualTo("Use \\<b> for bold, a < b.\n\nReturns List\\<E> items.\n\n"
                + "See [Set\\<T>](<https://example.org/a(b)>).\n");
    }

    @Test
    void internalAndUnsafeLinksStayText() throws IOException {
        // Word's table of contents links to bookmarks; javascript: must not reach a Markdown renderer
        byte[] docx = TestDocx.builder()
                .paragraphWithLink("", null, "", "Introduction")
                .paragraphWithLink("Click ", "javascript:alert(1)", ".", "here")
                .bytes();

        assertThat(convert(docx)).isEqualTo("Introduction\n\nClick here.\n");
    }

    @Test
    void addressShownAsLinkTextIsAnAutolink() throws IOException {
        // real form: the URL was typed with a trailing space, so the target ends with %20
        byte[] docx = TestDocx.builder()
                .paragraphWithLink("Use ", "https://example.org/form%20", ".", "https://example.org/form")
                .paragraphWithLink("Email ", "mailto:team@example.org", ".", "team@example.org")
                .paragraphWithLink("", "https://example.org/a%20", "", "Form")
                .paragraphWithLink("Note 1", "https://example.org/b", " and", "https://example.org/b")
                .paragraphWithLink("", "https://example.org/c", " first", "https://example.org/c")
                .bytes();

        assertThat(convert(docx)).isEqualTo("""
                Use <https://example.org/form>.

                Email <team@example.org>.

                [Form](https://example.org/a)

                Note 1<https://example.org/b> and

                <https://example.org/c> first
                """);
    }

    @Test
    void escapesBracketsInLinkTextAndParenthesesInUrl() throws IOException {
        byte[] docx = TestDocx.builder()
                .paragraphWithLink("", "https://en.wikipedia.org/wiki/Java_(programming_language)", "", "Java [language]")
                .bytes();

        assertThat(convert(docx))
                .isEqualTo("[Java \\[language\\]](<https://en.wikipedia.org/wiki/Java_(programming_language)>)\n");
    }

    @Test
    void readsFromPathToo() throws IOException {
        Path docx = Files.write(dir.resolve("letter.docx"), TestDocx.builder().paragraph("Dear Doctor").bytes());

        assertThat(converter.convert(docx).markdown()).isEqualTo("Dear Doctor\n");
    }

    @Test
    void passwordProtectedDocxGetsAClearMessage() throws Exception {
        // Word encrypts a DOCX into an OLE2 container; POI then reports "OLE2 Format", not encryption
        byte[] plain = TestDocx.builder().paragraph("secret").bytes();
        Path docx = dir.resolve("protected.docx");
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            Encryptor encryptor = new EncryptionInfo(EncryptionMode.agile).getEncryptor();
            encryptor.confirmPassword("password");
            try (OutputStream out = encryptor.getDataStream(fs)) {
                out.write(plain);
            }
            try (OutputStream out = Files.newOutputStream(docx)) {
                fs.writeFilesystem(out);
            }
        }

        assertThatThrownBy(() -> converter.convert(docx))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("password-protected");
        assertThatThrownBy(() -> converter.convert(new ByteArrayInputStream(Files.readAllBytes(docx)), "protected.docx"))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("password-protected");
    }

    @Test
    void wrapsDamagedDocxFile() throws IOException {
        // a valid package without a Word document: POI opens it, then XWPFDocument fails
        Path notWord = Files.write(dir.resolve("not-word.docx"), packageWithoutDocument());
        Path notZip = Files.writeString(dir.resolve("damaged.docx"), "this is not a DOCX");

        for (Path docx : new Path[] {notWord, notZip}) {
            assertThatThrownBy(() -> converter.convert(docx))
                    .isInstanceOf(DocumentConversionException.class)
                    .hasMessageContaining(docx.getFileName().toString());
        }
    }

    private static byte[] packageWithoutDocument() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"txt\" ContentType=\"text/plain\"/></Types>").getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("hello".getBytes(StandardCharsets.US_ASCII));
        }
        return bytes.toByteArray();
    }

    @Test
    void wrapsDamagedDocxAndDoesNotCloseTheStream() {
        TrackingStream in = new TrackingStream("this is not a DOCX".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> converter.convert(in, "damaged.docx"))
                .isInstanceOf(DocumentConversionException.class)
                .isNotInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("damaged.docx")
                .hasCauseInstanceOf(Exception.class);
        assertThat(in.closed).isFalse();
    }

    @Test
    void metadataComesFromTheDocumentProperties() throws IOException {
        PreparedDocument document = converter.convert(new ByteArrayInputStream(TestDocx.builder()
                .properties("Annual  report", "Ada Lovelace", "en-GB").paragraph("Text").bytes()), "a.docx");
        assertThat(document.title()).contains("Annual report");
        assertThat(document.author()).contains("Ada Lovelace");
        assertThat(document.language()).contains("en-GB");

        // Word writes the language in the default text style, not in the core properties
        PreparedDocument styled = converter.convert(new ByteArrayInputStream(TestDocx.builder()
                .defaultLanguage("de-DE").paragraph("Text").bytes()), "b.docx");
        assertThat(styled.language()).contains("de-DE");

        PreparedDocument bare = converter.convert(new ByteArrayInputStream(TestDocx.builder()
                .paragraph("Text").bytes()), "c.docx");
        assertThat(bare.title()).isEmpty();
        assertThat(bare.language()).isEmpty();
    }

    private String convert(byte[] docx) {
        return converter.convert(new ByteArrayInputStream(docx), "test.docx").markdown();
    }

    private static final class TrackingStream extends ByteArrayInputStream {

        boolean closed;

        TrackingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
