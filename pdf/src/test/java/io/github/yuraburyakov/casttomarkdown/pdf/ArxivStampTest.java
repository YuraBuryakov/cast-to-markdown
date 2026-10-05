package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArxivStampTest {
    private static final String EXPECTED = "arXiv:1512.03385v1 [cs.CV] 10 Dec 2015";
    private static List<Line> stamp() {
        return new ArrayList<>(List.of(new Line(1, 792, 232.0f, 32.0f, 20, false, true, "ar", -1, 15.54001f, 32, 560.0f),
                new Line(1, 792, 247.54001f, 32.0f, 20, false, true, "X", -1, 14.44f, 32, 544.45996f),
                new Line(1, 792, 261.98001f, 32.0f, 20, false, true, "iv", -1, 15.56f, 32, 530.02002f),
                new Line(1, 792, 277.54001f, 32.0f, 20, false, true, ":1", -1, 15.56f, 32, 514.45996f),
                new Line(1, 792, 293.10001f, 32.0f, 20, false, true, "51", -1, 20.0f, 32, 498.89999f),
                new Line(1, 792, 313.10001f, 32.0f, 20, false, true, "2.", -1, 15.0f, 32, 478.89999f),
                new Line(1, 792, 328.10001f, 32.0f, 20, false, true, "03", -1, 20.0f, 32, 463.89999f),
                new Line(1, 792, 348.10001f, 32.0f, 20, false, true, "38", -1, 20.0f, 32, 443.89999f),
                new Line(1, 792, 368.10001f, 32.0f, 20, false, true, "5v", -1, 20.0f, 32, 423.89999f),
                new Line(1, 792, 388.10001f, 32.0f, 20, false, true, "1 ", -1, 15.0f, 32, 403.89999f),
                new Line(1, 792, 403.10001f, 32.0f, 20, false, true, " [", -1, 11.66f, 32, 388.89999f),
                new Line(1, 792, 414.76001f, 32.0f, 20, false, true, "cs", -1, 16.66f, 32, 377.23999f),
                new Line(1, 792, 431.42001f, 32.0f, 20, false, true, ".C", -1, 18.34f, 32, 360.57999f),
                new Line(1, 792, 449.76001f, 32.0f, 20, false, true, "V", -1, 14.44f, 32, 342.23999f),
                new Line(1, 792, 464.20001f, 32.0f, 20, false, true, "] ", -1, 11.66f, 32, 327.79999f),
                new Line(1, 792, 475.86002f, 32.0f, 20, false, true, " 1", -1, 15.0f, 32, 316.13998f),
                new Line(1, 792, 490.86002f, 32.0f, 20, false, true, "0 ", -1, 15.0f, 32, 301.13998f),
                new Line(1, 792, 505.86002f, 32.0f, 20, false, true, "D", -1, 14.43997f, 32, 286.13998f),
                new Line(1, 792, 520.29999f, 32.0f, 20, false, true, "ec", -1, 17.76001f, 32, 271.70001f),
                new Line(1, 792, 538.06f, 32.0f, 20, false, true, " 2", -1, 15.0f, 32, 253.94f),
                new Line(1, 792, 553.06f, 32.0f, 20, false, true, "01", -1, 20.0f, 32, 238.94f),
                new Line(1, 792, 573.06f, 32.0f, 20, false, true, "5", -1, 10.0f, 32, 218.94f)));
    }
    private static List<Line> document(List<Line> stamp) {
        List<Line> lines = new ArrayList<>(List.of(
                new Line(1, 50, 120, 9, "First substantial body line with enough characters to find a margin."),
                new Line(1, 50, 131, 9, "Second substantial body line with enough characters to find a margin."),
                new Line(1, 50, 142, 9, "Third substantial body line with enough characters to find a margin.")));
        lines.addAll(stamp);
        lines.add(new Line(2, 50, 120, 9, "Next page body text stays in its original paragraph."));
        return lines;
    }
    private static Line changed(Line l, int page, boolean rotated, float pageX, float x, String text) {
        return new Line(page, l.pageHeight(), x, l.y(), l.fontSize(), l.bold(), rotated,
                text, l.table(), l.width(), pageX, l.pageY(), l.words());
    }
    private static String enabled(List<Line> lines) { return PdfConverter.toMarkdown(lines, List.of(), true); }
    private static String disabled(List<Line> lines) { return PdfConverter.toMarkdown(lines); }

    @Test
    void realMeasuredFragmentsBecomeOnePlainBlockWithoutChangingOtherOutput() {
        List<Line> pieces = stamp();
        List<Line> lines = document(pieces);
        String original = disabled(lines);
        String fragmented = pieces.stream().map(Line::text)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        // Normalization strips trailing spaces; per-line Markdown escaping is unchanged here.
        String expected = original.replace(fragmented, EXPECTED);
        assertThat(expected).isNotEqualTo(original);
        assertThat(enabled(lines)).isEqualTo(expected);
        assertThat(enabled(lines)).containsOnlyOnce(EXPECTED).doesNotContain("# " + EXPECTED);
        assertThat(lines).containsExactlyElementsOf(document(pieces));
    }

    @Test
    void missingFragmentOrBadPrefixDateIdAndGeometryLeaveEntireOutputUnchanged() {
        for (int variant = 0; variant < 6; variant++) {
            List<Line> pieces = stamp();
            if (variant == 0) pieces.remove(10);
            else {
                int index = variant == 1 ? 0 : variant == 2 ? 21 : variant == 3 ? 5 : 10;
                Line line = pieces.get(index);
                String text = variant == 1 ? "XX" : variant == 2 ? "?" : variant == 3 ? "9." : line.text();
                pieces.set(index, changed(line, 1, true, variant == 4 ? 33 : line.pageX(),
                        variant == 5 ? line.x() + 10 : line.x(), text));
            }
            List<Line> lines = document(pieces);
            assertThat(enabled(lines)).as("variant %s", variant).isEqualTo(disabled(lines));
        }
    }

    @Test
    void interruptedChainIsNeverPartiallyJoined() {
        List<Line> pieces = stamp();
        pieces.add(10, new Line(1, 50, 300, 9, "Ordinary body text inside the sequence stays intact."));
        List<Line> lines = document(pieces);
        assertThat(enabled(lines)).isEqualTo(disabled(lines));
    }

    @Test
    void extraPrefixInSameBandRejectsFullChainAndItsOtherwiseValidSuffix() {
        List<Line> pieces = stamp();
        Line first = pieces.get(0);
        pieces.add(0, new Line(1, 792, first.x() - 1, first.y(), 20, false, true, "x", -1,
                1, first.pageX(), first.pageY() + 1));
        List<Line> lines = document(pieces);
        assertThat(enabled(lines)).isEqualTo(disabled(lines));
    }

    @Test
    void secondPageBodyAreaAndNonRotatedFragmentsStayUnchanged() {
        for (int variant = 0; variant < 3; variant++) {
            int mode = variant;
            List<Line> pieces = stamp().stream().map(l -> changed(l, mode == 0 ? 2 : 1,
                    mode != 2, mode == 1 ? 60 : l.pageX(), l.x(), l.text())).toList();
            List<Line> lines = document(pieces);
            assertThat(enabled(lines)).isEqualTo(disabled(lines));
        }
    }

    @Test
    void unknownOrRotatedPageAndMissingBodyAnchorAreDisabled() {
        List<Line> lines = document(stamp());
        assertThat(PdfConverter.toMarkdown(lines, List.of(), false)).isEqualTo(disabled(lines));
        assertThat(enabled(stamp())).isEqualTo(disabled(stamp()));
    }

    @Test
    void axisAndShortOrdinaryTextArePreservedAndHeadingLevelsDoNotChange() {
        List<Line> lines = document(stamp());
        lines.add(0, new Line(1, 50, 80, 14, "Document heading"));
        lines.add(new Line(2, 792, 200, 32, 8, false, true, "training error", -1, 50, 314, 280));
        lines.add(new Line(2, 50, 160, 9, "ar"));
        List<List<Line>> paragraphs = Paragraphs.group(PageFurniture.remove(Lists.attachMarkers(lines)));
        int[] headings = Headings.levels(paragraphs);
        String actual = enabled(lines);
        assertThat(actual).contains("training error", "ar", "Document heading", EXPECTED);
        assertThat(Headings.levels(paragraphs)).containsExactly(headings);
        assertThat(actual.lines().filter(l -> l.startsWith("#")).toList())
                .isEqualTo(disabled(lines).lines().filter(l -> l.startsWith("#")).toList());
    }
}
