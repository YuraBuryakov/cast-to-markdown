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
    void keepsTheHyphenButNotTheLineBreakWhenTheDocumentDoesNotShow() {
        // "high-level" or "highlevel"? The document has neither, so the hyphen stays; the word stays whole.
        List<String> lines = List.of("solving high-", "level vision tasks", "end with -", "Upper case after");

        assertThat(Hyphens.join(lines, Set.of("vision")))
                .containsExactly("solving high-level", "vision tasks", "end with -", "Upper case after");
    }

    @Test
    void bothFormsExistKeepsTheHyphen() {
        List<String> lines = List.of("the foo-", "bar values");

        assertThat(Hyphens.join(lines, Set.of("foobar", "foo-bar"))).containsExactly("the foo-bar", "values");
    }

    @Test
    void anEndingThatIsNoWordJoinsWithoutTheDocument() {
        // arXiv 1810.04805: "surpris-" / "ing", written nowhere else
        List<String> lines = List.of("is surpris-", "ing that", "the fac-", "tions of");

        assertThat(Hyphens.join(lines, Set.of())).containsExactly("is surprising", "that", "the factions", "of");
    }

    @Test
    void anEndingTheDocumentWritesWithHyphenKeepsIt() {
        List<String> lines = List.of("the well-", "ness score");

        assertThat(Hyphens.join(lines, Set.of("well-ness"))).containsExactly("the well-ness", "score");
    }

    @Test
    void aCompoundGoingOnOverTheBreakStaysWhole() {
        // arXiv 1608.06993: "state-of-" / "the-art"
        List<String> lines = List.of("achieves state-of-", "the-art results");

        assertThat(Hyphens.join(lines, Set.of())).containsExactly("achieves state-of-the-art", "results");
    }

    /**
     * Breaks labelled by hand in the sample corpus (Q-PDF-HYPH): word break, compound, unclear. Columns: the
     * two lines, the words the document writes elsewhere, the expected first line. No compound loses its hyphen.
     */
    @Test
    void labelledBreaksOfTheSampleCorpus() {
        String[][] cases = {
                // word breaks: joined when the document or the ending shows it, else the hyphen stays
                {"rather surpris-", "ing that", "", "rather surprising"},
                {"a short descrip-", "tion of", "", "a short description"},
                {"for fast un-", "supervised sentence", "unsupervised", "for fast unsupervised"},
                {"It is sur-", "prisingly good", "", "It is sur-prisingly"},
                {"the multi-", "plicative attention", "", "the multi-plicative"},
                // compounds, mostly drawn by Chrome, Word and WeasyPrint, which break only at a hyphen
                {"English-", "to-German translation", "", "English-to-German"},
                {"high-", "level features", "", "high-level"},
                {"the sequence-", "aligned RNNs", "", "the sequence-aligned"},
                {"Supplier's non-", "promotional price", "non-promotional", "Supplier's non-promotional"},
                {"cannot be re-", "issued", "", "cannot be re-issued"},
                // unclear: the document writes both forms
                {"a feed-", "forward network", "feedforward feed-forward", "a feed-forward"},
                {"for down-", "stream tasks", "downstream down-stream", "for down-stream"},
        };
        for (String[] c : cases) {
            Set<String> words = c[2].isEmpty() ? Set.of() : Set.of(c[2].split(" "));
            assertThat(Hyphens.join(List.of(c[0], c[1]), words).get(0)).as(c[0] + c[1]).isEqualTo(c[3]);
        }
    }

    @Test
    void aSpaceAfterTheHyphenAtTheLineEndIsNoMatter() {
        // Word draws a space after the hyphen it wraps at: "Supplier's non- " / "promotional"
        List<String> lines = List.of("the Supplier's non- ", "promotional price.");

        assertThat(Hyphens.join(lines, Set.of())).containsExactly("the Supplier's non-promotional", "price.");
    }

    @Test
    void textMovedToTheStartOfALineIsEscaped() {
        List<String> lines = List.of("a high-", "level # sign");

        assertThat(Hyphens.join(lines, Set.of())).containsExactly("a high-level", "\\# sign");
    }

    @Test
    void capitalAfterTheHyphenIsLeftAlone() {
        // a name or a new sentence, not the rest of a word: "Jean-" / "Paul"
        List<String> lines = List.of("written by Jean-", "Paul Sartre");

        assertThat(Hyphens.join(lines, Set.of("jeanpaul", "jean-paul"))).isEqualTo(lines);
    }

    @Test
    void chainedBreaksAreJoinedOneAfterAnother() {
        List<String> lines = List.of("learn-", "ing deep-", "er models.");

        assertThat(Hyphens.join(lines, Set.of("learning", "deeper")))
                .containsExactly("learning", "deeper", "models.");
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
