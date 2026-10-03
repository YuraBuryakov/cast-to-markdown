package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Line-end hyphens, with examples from the arXiv ResNet paper (LaTeX, justified). */
class HyphensTest {

    @Test
    void joinsAWordTheDocumentWritesWithoutHyphen() {
        List<String> lines = List.of("Deeper neural networks are harder to train. Learn-", "ing, instead of fitting");

        assertThat(Hyphens.join(lines, Set.of("learning")))
                .containsExactly("Deeper neural networks are harder to train. Learning,", "instead of fitting");
    }

    @Test
    void keepsTheHyphenOfACompoundTheDocumentWritesWithHyphen() {
        List<String> lines = List.of("a multi-", "layer perceptron");

        assertThat(Hyphens.join(lines, Set.of("multi-layer"))).containsExactly("a multi-layer", "perceptron");
    }

    @Test
    void leavesLinesAsTheyAreWhenTheDocumentDoesNotShow() {
        // "high-level" or "highlevel"? The document has neither, so nothing is guessed.
        List<String> lines = List.of("solving high-", "level vision tasks", "end with -", "Upper case after");

        assertThat(Hyphens.join(lines, Set.of("vision"))).isEqualTo(lines);
    }

    @Test
    void aMovedWordCanEmptyTheNextLine() {
        List<String> lines = List.of("com-", "prehensive", "results");

        assertThat(Hyphens.join(lines, Set.of("comprehensive"))).containsExactly("comprehensive", "results");
    }

    @Test
    void wordsKeepTheirInnerHyphens() {
        assertThat(Hyphens.words(List.of(new Line(1, 72, 100, 10, "A multi-layer Net, 2 layers"))))
                .containsExactlyInAnyOrder("a", "multi-layer", "net", "layers");
    }
}
