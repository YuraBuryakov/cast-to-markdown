package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LateTextTest {

    @Test
    void manyPiecesOnOneBaselineTakeLinearTime() {
        // a hostile page: 200 000 one-word lines on one baseline, each far from the others, none joins
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            float x = i * 20;
            lines.add(new Line(1, 792, x, 100, 12, false, false, "w", -1, 6, x, 100,
                    List.of(new Line.Word("w", x, x + 6))));
        }

        List<Line> result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LateText.insert(lines));

        assertThat(result).hasSize(200_000);
    }

    @Test
    void smallerKeywordFillsTheGapLeftForIt() {
        // RFC 9562 page 8: "MAY" in 12 pt drawn after its 13 pt line, in the gap between "UUIDs" and "be"
        Line line = line(13, "UUIDs be represented", new Line.Word("UUIDs", 65.9f, 95.7f),
                new Line.Word("be", 121.8f, 133.3f), new Line.Word("represented", 135.9f, 193.5f));
        Line keyword = line(12, "MAY", new Line.Word("MAY", 98.3f, 119.2f));

        assertThat(LateText.insert(List.of(line, keyword))).extracting(Line::text)
                .containsExactly("UUIDs MAY be represented");
    }

    @Test
    void textOfAnotherSizeAfterTheLineStaysApart() {
        // arXiv 1512.00567: a formula piece on the same baseline right after the line, 1 pt off in size
        Line line = line(10, "the bounded gradient", new Line.Word("the", 112.5f, 124.7f),
                new Line.Word("bounded", 127.3f, 161.7f), new Line.Word("gradient", 164.3f, 197.0f));
        Line piece = line(9, ", reduces", new Line.Word(",", 199.5f, 202.0f), new Line.Word("reduces", 204.7f, 235.2f));

        assertThat(LateText.insert(List.of(line, piece))).hasSize(2);
    }

    private static Line line(float size, String text, Line.Word... words) {
        float x = words[0].left();
        return new Line(1, 792, x, 281.1f, size, false, false, text, -1, words[words.length - 1].right() - x, x, 281.1f,
                List.of(words));
    }
}
