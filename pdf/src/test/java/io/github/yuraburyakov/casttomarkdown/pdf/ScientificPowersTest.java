package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.junit.jupiter.api.Test;

class ScientificPowersTest {
    private static List<ScientificPowers.Glyph> chars(String text, float x, int exponent, float rise) {
        List<ScientificPowers.Glyph> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            boolean script = i >= exponent;
            float width = script ? 3 : 4.5f;
            result.add(new ScientificPowers.Glyph(text.charAt(i), x, x + width,
                    script ? 100 - rise : 100, script ? 6 : 9));
            x += width;
        }
        return result;
    }

    private static Line.Word word(List<ScientificPowers.Glyph> glyphs) {
        StringBuilder text = new StringBuilder();
        glyphs.forEach(g -> text.append(g.text()));
        return new Line.Word(text.toString(), glyphs.get(0).left(), glyphs.get(glyphs.size() - 1).right());
    }

    @SafeVarargs
    private static List<Line.Word> detect(List<ScientificPowers.Glyph>... glyphs) {
        return ScientificPowers.detect(java.util.Arrays.stream(glyphs).map(ScientificPowersTest::word).toList(), List.of(glyphs));
    }

    private static Line line(float y, List<Line.Word> words) {
        String text = words.stream().map(Line.Word::text).collect(java.util.stream.Collectors.joining(" "));
        return new Line(1, 792, words.get(0).left(), y, 9, false, false, text, -1, 200,
                words.get(0).left(), y, words);
    }

    @Test
    void inlinePowerPreservesGeometryAndSurroundingText() {
        List<Line.Word> words = detect(chars("60×", 100, 3, 0), chars("104", 115, 2, 3.6f));
        Line input = line(100, words);
        Line output = ScientificPowers.apply(input, List.of());
        assertThat(output.text()).isEqualTo("60× 10⁴");
        assertThat(output.words().get(1).left()).isEqualTo(words.get(1).left());
        assertThat(output.width()).isEqualTo(input.width());
        assertThat(ScientificPowers.cellText(words.get(1))).isEqualTo("104");
    }

    @Test
    void rejectsFootnoteOrdinaryNumberAndMissingMultiplier() {
        for (var glyphs : List.of(chars("competitions1", 100, 12, 3.6f),
                chars("1.8×109", 100, 7, 0), chars("109", 100, 2, 3.6f))) {
            assertThat(detect(glyphs).get(0).power()).isNull();
        }
    }

    @Test
    void rejectsDistantAndDifferentBaselineNeighbours() {
        assertThat(detect(chars("60×", 100, 3, 0), chars("104", 120, 2, 3.6f)).get(1).power()).isNull();
        var before = chars("60×", 100, 3, 0).stream()
                .map(g -> new ScientificPowers.Glyph(g.text(), g.left(), g.right(), 101, g.size())).toList();
        assertThat(detect(before, chars("104", 115, 2, 3.6f)).get(1).power()).isNull();
    }

    @Test
    void nearestGeometricWordAndInterveningTextPreventWrongContext() {
        assertThat(detect(chars("60×", 100, 3, 0), chars("x", 114, 1, 0),
                chars("104", 119, 2, 3.6f)).get(2).power()).isNull();
        // Stream order is reversed, but the nearest left word is still the multiplier.
        assertThat(detect(chars("104", 115, 2, 3.6f), chars("60×", 100, 3, 0)).get(0).power()).isNotNull();
    }

    @Test
    void verticalRuleBlocksCrossWordPower() {
        Line input = line(100, detect(chars("60×", 100, 3, 0), chars("104", 115, 2, 3.6f)));
        assertThat(ScientificPowers.apply(input, List.of(new PageGraphics.Box(114, 90, 114.4f, 110))))
                .isSameAs(input);
    }

    @Test
    void ruledTableKeepsColumnAndEmptyCell() {
        var power = detect(chars("1.8×109", 170, 6, 2.35f)).get(0);
        List<Line> lines = List.of(line(81, List.of(new Line.Word("label", 130, 150),
                        new Line.Word("FLOPs", 170, 195))),
                line(100, List.of(new Line.Word("model", 130, 150), power)),
                line(135, List.of(new Line.Word("Table", 130, 150), new Line.Word("1.", 154, 159))));
        List<PageGraphics.Box> rules = List.of(new PageGraphics.Box(126, 72, 300, 72.4f),
                new PageGraphics.Box(126, 84, 300, 84.4f), new PageGraphics.Box(126, 120, 300, 120.4f),
                new PageGraphics.Box(160, 72, 160.4f, 120), new PageGraphics.Box(240, 72, 240.4f, 120));
        List<String> tables = new ArrayList<>();
        RuledTables.replace(lines, Map.of(1, rules), tables);
        assertThat(tables).containsExactly(TableMarkdown.of(List.of(List.of("label", "FLOPs", ""),
                List.of("model", "1.8×10⁹", "")), false));
    }

    @Test
    void dollarsAndExistingUnicodeArePreserved() {
        var powered = detect(chars("1.8×109", 110, 6, 3.6f)).get(0);
        Line input = line(100, List.of(new Line.Word("$5", 90, 99), powered, new Line.Word("10⁴", 150, 165)));
        assertThat(ScientificPowers.apply(input, List.of()).text()).isEqualTo("$5 1.8×10⁹ 10⁴");
    }

    @Test
    void collectorDetectsRealPdfGlyphsButLeavesTaggedDocumentAlone() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            var font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float x = 100;
                for (String part : List.of("60× ", "10", "4", " iterations")) {
                    float size = part.equals("4") ? 6 : 9;
                    content.beginText();
                    content.setFont(font, size);
                    content.newLineAtOffset(x, part.equals("4") ? 503.6f : 500);
                    content.showText(part);
                    content.endText();
                    x += font.getStringWidth(part) * size / 1000;
                }
            }
            List<Line> lines = LineCollector.collect(document, TaggedTables.read(document)).lines();
            assertThat(ScientificPowers.apply(document, lines).stream().map(Line::text))
                    .containsExactly("60× 10⁴ iterations");
            document.getDocumentCatalog().setStructureTreeRoot(new PDStructureTreeRoot());
            assertThat(LineCollector.collect(document, TaggedTables.read(document)).lines().stream()
                    .flatMap(l -> l.words().stream()).map(Line.Word::power)).allMatch(p -> p == null);
        }
    }
}
