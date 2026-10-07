package io.github.yuraburyakov.casttomarkdown.docx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

/** Builds small DOCX files in memory for tests, so tests do not depend on binary fixtures. */
final class TestDocx {

    private final XWPFDocument document = new XWPFDocument();
    private BigInteger bulletList;
    private BigInteger numberedList;

    static TestDocx builder() {
        return new TestDocx();
    }

    /** A paragraph with a built-in style id such as {@code "Title"} or {@code "Heading2"}. */
    TestDocx styled(String styleId, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(styleId);
        paragraph.createRun().setText(text);
        return this;
    }

    TestDocx paragraph(String text) {
        document.createParagraph().createRun().setText(text);
        return this;
    }

    TestDocx bodySdt(String... paragraphs) {
        var content = document.getDocument().getBody().addNewSdt().addNewSdtContent();
        for (String text : paragraphs) {
            content.addNewP().addNewR().addNewT().setStringValue(text);
        }
        return this;
    }

    TestDocx inlineSdt(String text) {
        document.createParagraph().getCTP().addNewSdt().addNewSdtContent()
                .addNewR().addNewT().setStringValue(text);
        return this;
    }

    TestDocx bullet(int level, String text) {
        if (bulletList == null) {
            bulletList = list(STNumberFormat.BULLET);
        }
        return item(bulletList, level, text);
    }

    TestDocx numbered(int level, String text) {
        if (numberedList == null) {
            numberedList = list(STNumberFormat.DECIMAL);
        }
        return item(numberedList, level, text);
    }

    /** Numbered items from now on belong to a new list whose levels start at {@code start}. */
    TestDocx newNumberedList(int start) {
        numberedList = list(STNumberFormat.DECIMAL, BigInteger.valueOf(10 + start), start);
        return this;
    }

    /**
     * Numbered items from now on belong to a new list instance ({@code w:num}) of the same definition,
     * as Word writes "Restart numbering" ({@code startOverride}) or a pasted list ({@code null}: no override).
     */
    TestDocx newNumberedInstance(Integer startOverride) {
        XWPFNumbering numbering = document.getNumbering();
        numberedList = numbering.addNum(numbering.getAbstractNumID(numberedList));
        if (startOverride != null) {
            CTNumLvl override = numbering.getNum(numberedList).getCTNum().addNewLvlOverride();
            override.setIlvl(BigInteger.ZERO);
            override.addNewStartOverride().setVal(BigInteger.valueOf(startOverride));
        }
        return this;
    }

    /** The first row is the header. */
    TestDocx table(String[]... rows) {
        XWPFTable table = document.createTable(rows.length, rows[0].length);
        for (int r = 0; r < rows.length; r++) {
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < rows[r].length; c++) {
                row.getCell(c).setText(rows[r][c]);
            }
        }
        return this;
    }

    /** A one-column table: a header, then one cell with two paragraphs, the second ending in a link. */
    TestDocx tableWithTwoParagraphCell(String header, String first, String second, String linkText, String url) {
        XWPFTable table = document.createTable(2, 1);
        table.getRow(0).getCell(0).setText(header);
        XWPFTableCell cell = table.getRow(1).getCell(0);
        cell.setText(first);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.createRun().setText(second);
        paragraph.createHyperlinkRun(url).setText(linkText);
        return this;
    }

    TestDocx paragraphWithFootnote(String text, String footnoteText) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(text);
        XWPFFootnote footnote = document.createFootnote();
        footnote.createParagraph().createRun().setText(footnoteText);
        paragraph.addFootnoteReference(footnote);
        return this;
    }

    /**
     * A paragraph {@code before + link + after}; the link text is split into one run per part, as Word does
     * when the formatting changes inside a link. A {@code null} url makes an internal link to a bookmark.
     */
    TestDocx paragraphWithLink(String before, String url, String after, String... linkParts) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(before);
        CTHyperlink link;
        if (url != null) {
            XWPFHyperlinkRun first = paragraph.createHyperlinkRun(url);
            first.setText(linkParts[0]);
            link = first.getCTHyperlink();
        } else {
            link = paragraph.getCTP().addNewHyperlink();
            link.setAnchor("_Toc1");
            link.addNewR().addNewT().setStringValue(linkParts[0]);
        }
        for (int i = 1; i < linkParts.length; i++) {
            CTText text = link.addNewR().addNewT();
            text.setStringValue(linkParts[i]);
            text.setSpace(SpaceAttribute.Space.PRESERVE);
        }
        paragraph.createRun().setText(after);
        return this;
    }

    byte[] bytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.write(out);
        document.close();
        return out.toByteArray();
    }

    private TestDocx item(BigInteger list, int level, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setNumID(list);
        paragraph.setNumILvl(BigInteger.valueOf(level));
        paragraph.createRun().setText(text);
        return this;
    }

    /** A list definition with the same number format on three levels. */
    private BigInteger list(STNumberFormat.Enum format) {
        return list(format, BigInteger.valueOf(format == STNumberFormat.BULLET ? 1 : 2), 1);
    }

    private BigInteger list(STNumberFormat.Enum format, BigInteger abstractId, int start) {
        XWPFNumbering numbering = document.getNumbering() != null ? document.getNumbering() : document.createNumbering();
        CTAbstractNum definition = CTAbstractNum.Factory.newInstance();
        definition.setAbstractNumId(abstractId);
        for (int level = 0; level < 3; level++) {
            CTLvl lvl = definition.addNewLvl();
            lvl.setIlvl(BigInteger.valueOf(level));
            lvl.addNewNumFmt().setVal(format);
            lvl.addNewStart().setVal(BigInteger.valueOf(start));
        }
        numbering.addAbstractNum(new XWPFAbstractNum(definition, numbering));
        return numbering.addNum(abstractId);
    }
}
