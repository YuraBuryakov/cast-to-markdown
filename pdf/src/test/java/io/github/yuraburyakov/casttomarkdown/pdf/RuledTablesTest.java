package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RuledTablesTest {

    private final List<String> tables = new ArrayList<>();

    @Test
    void gridKeepsEmptyCellsAndMergedHeaderCells() {
        // arXiv Table 9: "COCO train" spans columns 2-3 (no rule at 324 in that row), "ensemble" has two
        // empty cells before 59.0; the 3 data rows share one band between horizontal rules
        List<PageGraphics.Box> rules = new ArrayList<>(List.of(rule(140, 455, 72), rule(140, 455, 84),
                rule(140, 455, 96), rule(140, 455, 108), rule(140, 455, 166), rule(140, 455, 178),
                vertical(283, 72, 96), vertical(369, 72, 96)));
        for (float x : new float[] {283, 324, 369, 410}) {
            rules.add(vertical(x, 96, 178));
        }
        Line caption = line(196, w("Table", 140), w("9.", 164), w("Object", 176), w("detection.", 204));
        List<Line> lines = List.of(
                line(81, w("training", 144), w("data", 180), w("COCO", 304), w("train", 331), w("COCO", 385),
                        w("trainval", 412)),
                line(105, w("mAP", 144), w("@.5", 296), w("@[.5,.95]", 328), w("@.5", 382), w("@[.5,.95]", 415)),
                line(116, w("baseline", 144), w("41.5", 296), w("21.2", 339)),
                line(128, w("+box", 146), w("49.9", 296), w("29.9", 339)),
                line(140, w("+context", 146), w("51.1", 296), w("30.0", 339), w("53.3", 382), w("32.2", 425)),
                line(174, w("ensemble", 144), w("59.0", 382), w("37.4", 425)),
                caption);

        List<Line> result = RuledTables.replace(lines, Map.of(1, rules), tables);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).table()).isZero();
        assertThat(result.get(1)).isSameAs(caption);
        assertThat(tables).containsExactly(TableMarkdown.of(List.of(
                List.of("training data", "COCO train", "", "COCO trainval", ""),
                List.of("mAP", "@.5", "@[.5,.95]", "@.5", "@[.5,.95]"),
                List.of("baseline", "41.5", "21.2", "", ""),
                List.of("+box", "49.9", "29.9", "", ""),
                List.of("+context", "51.1", "30.0", "53.3", "32.2"),
                List.of("ensemble", "", "", "59.0", "37.4")), false));
    }

    @Test
    void bandWithoutALabelOnEveryLineIsOneRow() {
        // arXiv Table 1: "conv2" once, next to stacked lines in the other columns
        List<Line> lines = List.of(
                line(81, w("layer", 130), w("18-layer", 170), w("34-layer", 250)),
                line(92, w("3×3,", 170), w("64", 190), w("3×3,", 250), w("64", 270)),
                line(102, w("conv2", 130)),
                line(112, w("3×3,", 170), w("64", 190), w("3×3,", 250), w("64", 270)),
                line(135, w("Table", 130), w("1.", 154), w("Architectures.", 166)));

        RuledTables.replace(lines, Map.of(1, smallGrid()), tables);

        assertThat(tables).containsExactly(TableMarkdown.of(List.of(
                List.of("layer", "18-layer", "34-layer"),
                List.of("conv2", "3×3, 64 3×3, 64", "3×3, 64 3×3, 64")), false));
    }

    @Test
    void underscoreDrawnAsARuleDoesNotCutTheRow() {
        // arXiv 1512.03385 Table 1: the underscore of "conv2_x" is a rule 1.9 pt wide in the middle of the row
        List<PageGraphics.Box> rules = new java.util.ArrayList<>(smallGrid());
        rules.add(new PageGraphics.Box(150, 102, 151.9f, 102));
        List<Line> lines = List.of(
                line(81, w("layer", 130), w("18-layer", 170), w("34-layer", 250)),
                line(92, w("3×3,", 170), w("64", 190), w("3×3,", 250), w("64", 270)),
                line(102, w("conv2", 130), w("x", 152)),
                line(112, w("3×3,", 170), w("64", 190), w("3×3,", 250), w("64", 270)),
                line(135, w("Table", 130), w("1.", 154), w("Architectures.", 166)));

        RuledTables.replace(lines, Map.of(1, rules), tables);

        assertThat(tables).containsExactly(TableMarkdown.of(List.of(
                List.of("layer", "18-layer", "34-layer"),
                List.of("conv2 x", "3×3, 64 3×3, 64", "3×3, 64 3×3, 64")), false));
    }

    @Test
    void cellSpanningUnderAHeaderWithMoreColumnsGoesToTheFirstOfThem() {
        // arXiv Table 1, conv1: the header has a rule between every model, the conv1 row only after the label;
        // the row's line sits so close under the header that a font size above its baseline reaches into it
        List<PageGraphics.Box> rules = List.of(rule(126, 300, 72), rule(126, 300, 84), rule(126, 300, 96),
                vertical(160, 72, 96), vertical(200, 72, 84), vertical(250, 72, 84));
        List<Line> lines = List.of(
                line(81, w("layer", 130), w("18-layer", 165), w("34-layer", 205), w("50-layer", 255)),
                line(90, w("conv1", 130), w("7×7,", 215), w("64", 235)),
                line(111, w("Table", 130), w("1.", 154), w("Architectures.", 166)));

        RuledTables.replace(lines, Map.of(1, rules), tables);

        assertThat(tables).containsExactly(TableMarkdown.of(List.of(
                List.of("layer", "18-layer", "34-layer", "50-layer"),
                List.of("conv1", "7×7, 64", "", "")), false));
    }

    @Test
    void captionInTheParagraphOfTheRowsIsFound() {
        // arXiv Table 13: the caption follows the last row in the same font, 15 points below it, and the
        // lines 12 points apart make that one paragraph
        List<Line> lines = List.of(
                line(81, w("layer", 130), w("18-layer", 170)),
                line(93, w("a", 130), w("1", 170)),
                line(105, w("b", 130), w("2", 170)),
                line(117, w("c", 130), w("3", 170)),
                line(132, w("Table", 130), w("13.", 154), w("Localization.", 172)));
        assertThat(Paragraphs.group(lines)).hasSize(1);

        RuledTables.replace(lines, Map.of(1, smallGrid()), tables);

        assertThat(tables).containsExactly(TableMarkdown.of(List.of(List.of("layer", "18-layer"),
                List.of("a", "1"), List.of("b", "2"), List.of("c", "3")), false));
    }

    @Test
    void rulesWithoutVerticalsAreNoGrid() {
        List<Line> lines = List.of(line(81, w("layer", 130), w("18-layer", 170)),
                line(135, w("Table", 130), w("1.", 154), w("Results.", 166)));

        assertThat(RuledTables.replace(lines, Map.of(1, List.of(rule(126, 300, 72), rule(126, 300, 120))), tables))
                .isEqualTo(lines);
        assertThat(tables).isEmpty();
    }

    @Test
    void lineStickingOutOfTheGridCancelsTheTable() {
        // the grid is next to text of the other column on the same baseline
        List<Line> lines = List.of(
                line(81, w("layer", 130), w("18-layer", 170), w("34-layer", 250)),
                line(102, w("conv2", 130), w("3×3,", 170), w("residual", 400)),
                line(135, w("Table", 130), w("1.", 154), w("Architectures.", 166)));

        assertThat(RuledTables.replace(lines, Map.of(1, smallGrid()), tables)).isEqualTo(lines);
        assertThat(tables).isEmpty();
    }

    @Test
    void captionWithoutGridChangesNothing() {
        List<Line> lines = List.of(line(81, w("layer", 130), w("18-layer", 170)),
                line(135, w("Table", 130), w("1.", 154), w("Results.", 166)));

        assertThat(RuledTables.replace(lines, Map.of(), tables)).isEqualTo(lines);
        assertThat(tables).isEmpty();
    }

    @Test
    void tableMentionIsNoCaption() {
        List<Line> lines = List.of(line(81, w("layer", 130), w("18-layer", 170)),
                line(135, w("Table", 130), w("1", 154), w("shows", 166), w("the", 190), w("results", 204)));

        assertThat(RuledTables.replace(lines, Map.of(1, smallGrid()), tables)).isEqualTo(lines);
        assertThat(tables).isEmpty();
    }

    @Test
    void clusterOfTooManyRulesIsNoTable() {
        List<PageGraphics.Box> rules = new ArrayList<>(smallGrid());
        for (int i = 0; i < 1_000; i++) {
            rules.add(vertical(130 + i % 25, 73, 83));
        }
        List<Line> lines = List.of(line(81, w("layer", 130), w("18-layer", 170)),
                line(135, w("Table", 130), w("1.", 154), w("Results.", 166)));

        assertThat(RuledTables.replace(lines, Map.of(1, rules), tables)).isEqualTo(lines);
        assertThat(tables).isEmpty();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void manyCaptionsNextToManyRulesAreFast() {
        // untrusted input: thousands of separate rules and thousands of captions on one page
        List<PageGraphics.Box> rules = new ArrayList<>();
        for (int i = 0; i < 9_000; i++) {
            rules.add(rule(i % 100 * 8, i / 100 * 8, i % 100 * 8 + 3));
        }
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            lines.add(line(100 + i * 20, w("Table", 0), w(i + ".", 30), w("Results", 60), w("of", 700)));
        }

        RuledTables.replace(lines, Map.of(1, rules), tables);

        assertThat(tables).isEmpty();
    }

    /** Three columns (126-160-240-300), a header band 72-84 and a body band 84-120. */
    private static List<PageGraphics.Box> smallGrid() {
        return List.of(rule(126, 300, 72), rule(126, 300, 84), rule(126, 300, 120),
                vertical(160, 72, 120), vertical(240, 72, 120));
    }

    private static PageGraphics.Box rule(float left, float right, float y) {
        return new PageGraphics.Box(left, y, right, y + 0.4f);
    }

    private static PageGraphics.Box vertical(float x, float top, float bottom) {
        return new PageGraphics.Box(x, top, x + 0.4f, bottom);
    }

    /** A word 4 points per character wide. */
    private static Line.Word w(String text, float left) {
        return new Line.Word(text, left, left + text.length() * 4);
    }

    /** An 8 pt line on page 1 made of the words. */
    private static Line line(float y, Line.Word... words) {
        String text = Arrays.stream(words).map(Line.Word::text).collect(Collectors.joining(" "));
        float left = words[0].left();
        float right = words[words.length - 1].right();
        return new Line(1, 792, left, y, 8, false, false, text, -1, right - left, left, y, List.of(words));
    }
}
