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
        // a hostile page: 30 000 one-word lines on one baseline, each far from the others, none joins
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 30_000; i++) {
            float x = i * 20;
            lines.add(new Line(1, 792, x, 100, 12, false, false, "w", -1, 6, x, 100,
                    List.of(new Line.Word("w", x, x + 6))));
        }

        List<Line> result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LateText.insert(lines));

        assertThat(result).hasSize(30_000);
    }
}
