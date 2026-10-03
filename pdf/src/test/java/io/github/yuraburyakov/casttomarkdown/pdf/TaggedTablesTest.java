package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Hostile structure trees: built in memory, as PDFBox itself overflows the stack saving the deep one. */
class TaggedTablesTest {

    @Test
    void veryDeepTreeDoesNotOverflowTheStack() throws IOException {
        try (PDDocument document = documentWithRoot()) {
            PDStructureElement parent = top(document);
            for (int i = 0; i < 100_000; i++) {
                PDStructureElement child = new PDStructureElement(i % 2 == 0 ? "Table" : "Div", parent);
                parent.appendKid(child);
                parent = child;
            }

            assertThat(TaggedTables.read(document).isEmpty()).isTrue();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void loopsAndSharedElementsAreWalkedOnce() throws IOException {
        try (PDDocument document = documentWithRoot()) {
            PDStructureElement top = top(document);
            // every level lists the next element twice: 2^60 paths without a visited set
            PDStructureElement level = top;
            for (int i = 0; i < 60; i++) {
                PDStructureElement next = new PDStructureElement("Div", level);
                level.appendKid(next);
                level.appendKid(next);
                level = next;
            }
            level.appendKid(top); // a loop back to the top: also makes the parent links (/P) a cycle

            assertThat(TaggedTables.read(document).isEmpty()).isTrue();
        }
    }

    private static PDDocument documentWithRoot() {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());
        document.getDocumentCatalog().setStructureTreeRoot(new PDStructureTreeRoot());
        return document;
    }

    private static PDStructureElement top(PDDocument document) {
        PDStructureTreeRoot root = document.getDocumentCatalog().getStructureTreeRoot();
        PDStructureElement top = new PDStructureElement("Document", root);
        root.appendKid(top);
        return top;
    }
}
