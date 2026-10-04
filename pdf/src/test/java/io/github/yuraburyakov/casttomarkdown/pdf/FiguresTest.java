package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FiguresTest {

    @Test
    void textInsideTheFigureAboveTheCaptionIsRemoved() {
        List<Line> lines = List.of(line(72, 100, "Text above the figure."), line(120, 160, "3x3 conv, 64"),
                line(120, 185, "relu"), line(72, 225, "Figure 1. A building block."), line(72, 260, "Text after."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(100, 140, 300, 200))))))
                .containsExactly("Text above the figure.", "Figure 1. A building block.", "Text after.");
    }

    @Test
    void axisLabelBetweenFigureAndCaptionIsRemoved() {
        List<Line> lines = List.of(line(150, 212, "iter. (1e4)"), line(72, 238, "Figure 1. Training error."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(100, 140, 300, 200))))))
                .containsExactly("Figure 1. Training error.");
    }

    @Test
    void figureBelowACaptionIsFoundWhenThereIsNoneAbove() {
        List<Line> lines = List.of(line(72, 100, "Figure 2: Layout."), line(120, 135, "field"),
                line(72, 220, "After."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(100, 115, 300, 170))))))
                .containsExactly("Figure 2: Layout.", "After.");
    }

    @Test
    void tableRulesAreNotAFigure() {
        List<Line> lines = List.of(line(120, 170, "model err"), line(72, 225, "Figure 1. Not a drawing."));

        assertThat(Figures.remove(lines, Map.of(1, List.of(box(100, 140, 300, 140.5f), box(100, 199.5f, 300, 200)))))
                .isEqualTo(lines);
    }

    @Test
    void graphicsAroundTheCaptionAreABackground() {
        List<Line> lines = List.of(line(120, 170, "ASCII art"), line(72, 225, "Figure 1: Layout."));

        assertThat(Figures.remove(lines, Map.of(1, List.of(box(0, 0, 612, 792))))).isEqualTo(lines);
    }

    @Test
    void figureMentionInsideAParagraphIsNoCaption() {
        // arXiv p5: "... as shown in" / "Fig. 4. First, the situation is reversed"
        List<Line> lines = List.of(line(120, 70, "label"), line(72, 100, "results are shown in"),
                line(72, 112, "Fig. 4. First, the situation is reversed"));

        assertThat(Figures.remove(lines, Map.of(1, List.of(box(100, 60, 300, 85))))).isEqualTo(lines);
    }

    @Test
    void captionWithoutGraphicsChangesNothing() {
        List<Line> lines = List.of(line(120, 170, "a b c"), line(72, 225, "Figure 1: Layout."));

        assertThat(Figures.remove(lines, Map.of())).isEqualTo(lines);
    }

    @Test
    void graphicsInAnotherColumnAreNotTheFigure() {
        // the caption runs from x 72 to 147, the drawing from 320 to 520
        List<Line> lines = List.of(line(330, 170, "conv"), line(72, 225, "Figure 1. Left."));

        assertThat(Figures.remove(lines, Map.of(1, List.of(box(320, 140, 520, 200))))).isEqualTo(lines);
    }

    @Test
    void drawingInSeveralPiecesIsOneFigure() {
        // arXiv Figure 3: three network columns, the left one cut into pieces by "pool, /2" text; only the
        // lowest piece is near the caption, the others join it in chains of gaps up to 3 font sizes
        List<Line> lines = List.of(line(72, 120, "Body text above."), line(110, 190, "3x3 conv, 64"),
                line(170, 200, "3x3 conv, 128"), line(170, 270, "34-layer plain"), line(72, 300, "Figure 3. Networks."));

        List<PageGraphics.Box> pieces = List.of(box(100, 240, 140, 285), box(100, 150, 140, 232),
                box(160, 150, 200, 250));

        assertThat(texts(Figures.remove(lines, Map.of(1, pieces))))
                .containsExactly("Body text above.", "Figure 3. Networks.");
    }

    @Test
    void frameTouchingTheCaptionStillBelongsToIt() {
        // arXiv Figure 6: the frame ends 2 points below the estimated top of the caption, above its baseline
        List<Line> lines = List.of(line(120, 170, "iter. (1e4)"), line(72, 225, "Figure 6. Training."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(100, 140, 300, 222))))))
                .containsExactly("Figure 6. Training.");
    }

    @Test
    void axisLabelJustAboveTheCaptionIsRemoved() {
        // arXiv Figure 6: the axis label's baseline is 3 points above the caption's, below its estimated top;
        // the label is in a smaller font, so it is a paragraph of its own
        List<Line> lines = List.of(line(150, 222, 6, "iter. (1e4)"), line(72, 225, "Figure 6. Training."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(100, 140, 300, 205))))))
                .containsExactly("Figure 6. Training.");
    }

    @Test
    void figuresOfTheOtherColumnAreNotJoined() {
        // Inception-v3 p4: drawings of both columns 20 points apart; the left caption must not take the text
        // of the right column
        List<Line> lines = List.of(line(100, 150, "conv"), line(310, 215, "Figure 5. Right."),
                line(310, 250, "of the other"), line(310, 262, "column text"), line(50, 315, "Figure 4. Left side."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(50, 70, 280, 300), box(300, 70, 540, 200))))))
                .containsExactly("Figure 5. Right.", "of the other", "column text", "Figure 4. Left side.");
    }

    @Test
    void longLinesInsideAFigureStay() {
        // Learned Index p10: a table drawn as a figure; its rows are data, a short label is a label
        List<Line> lines = List.of(line(110, 160, "52.45 (4.00x) 274 (0.97x) 198 (72.3%) 51.93 (4.00x) 276"),
                line(110, 175, "page size"), line(72, 225, "Figure 4. Learned Index vs B-Tree."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(100, 140, 300, 200))))))
                .containsExactly("52.45 (4.00x) 274 (0.97x) 198 (72.3%) 51.93 (4.00x) 276",
                        "Figure 4. Learned Index vs B-Tree.");
    }

    @Test
    void labelsBesideTheDrawingWithinTheCaptionWidthAreRemoved() {
        // arXiv Figure 2: "F(x)" stands 31 points left of the blocks, still within the caption's width
        List<Line> lines = List.of(line(98, 114, "F(x)"), line(20, 120, "margin note"), line(107, 147, "F(x) + x"),
                line(87, 168, "Figure 2. Residual learning: a building block."));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(129, 81, 204, 154))))))
                .containsExactly("margin note", "Figure 2. Residual learning: a building block.");
    }

    @Test
    void drawingOnTheOtherSideOfTheCaptionIsNotJoined() {
        List<Line> lines = List.of(line(120, 170, "above"), line(72, 225, "Figure 1. Above."), line(120, 245, "below"));

        assertThat(texts(Figures.remove(lines, Map.of(1, List.of(box(100, 140, 300, 200), box(100, 232, 300, 260))))))
                .containsExactly("Figure 1. Above.", "below");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void pageWithTooManyPaintedBoxesIsLeftAsItIs() {
        // untrusted input: tens of thousands of tiny boxes would make clustering slow, so the page is skipped
        List<PageGraphics.Box> boxes = new ArrayList<>(List.of(box(100, 140, 300, 200)));
        for (int i = 0; i < 20_000; i++) {
            float x = i % 100 * 5;
            float y = 300 + i / 100 * 5f;
            boxes.add(box(x, y, x + 1, y + 1));
        }
        List<Line> lines = List.of(line(120, 170, "3x3 conv"), line(72, 225, "Figure 1. Many boxes."));

        assertThat(Figures.remove(lines, Map.of(1, boxes))).isEqualTo(lines);
    }

    /** A horizontal 10 pt line on page 1, 5 points per character wide. */
    private static Line line(float x, float y, String text) {
        return line(x, y, 10, text);
    }

    private static Line line(float x, float y, float size, String text) {
        return new Line(1, 792, x, y, size, false, false, text, -1, text.length() * size / 2, x, y);
    }

    private static PageGraphics.Box box(float left, float top, float right, float bottom) {
        return new PageGraphics.Box(left, top, right, bottom);
    }

    private static List<String> texts(List<Line> lines) {
        return lines.stream().map(Line::text).toList();
    }
}
