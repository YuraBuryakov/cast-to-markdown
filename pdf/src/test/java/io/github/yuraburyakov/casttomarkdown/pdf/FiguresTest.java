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
        return new Line(1, 792, x, y, 10, false, false, text, -1, text.length() * 5, x, y);
    }

    private static PageGraphics.Box box(float left, float top, float right, float bottom) {
        return new PageGraphics.Box(left, top, right, bottom);
    }

    private static List<String> texts(List<Line> lines) {
        return lines.stream().map(Line::text).toList();
    }
}
