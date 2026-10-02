package io.github.yuraburyakov.casttomarkdown.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LineCollectorTest {

    @Test
    void lineSizeIsTheSizeOfMostCharacters() {
        // Word 365: a glossary line of 94 characters in 12 pt with one opening quote in 14 pt.
        assertThat(LineCollector.dominantSize(Map.of(12f, 94, 14f, 1))).isEqualTo(12f);
    }

    @Test
    void largerSizeWinsOnTie() {
        assertThat(LineCollector.dominantSize(Map.of(9f, 5, 11f, 5))).isEqualTo(11f);
    }
}
