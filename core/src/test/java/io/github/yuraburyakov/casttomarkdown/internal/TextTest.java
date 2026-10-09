package io.github.yuraburyakov.casttomarkdown.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void utf8WithAndWithoutByteOrderMark() {
        byte[] text = "Grüße".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[text.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withBom, 3, text.length);

        assertThat(Text.decode(text)).isEqualTo("Grüße");
        assertThat(Text.decode(withBom)).isEqualTo("Grüße");
    }

    @Test
    void utf16ByItsByteOrderMark() {
        assertThat(Text.decode("﻿Привет".getBytes(StandardCharsets.UTF_16LE))).isEqualTo("Привет");
        assertThat(Text.decode("﻿Привет".getBytes(StandardCharsets.UTF_16BE))).isEqualTo("Привет");
    }

    @Test
    void bytesThatAreNoUtf8AreWindows1252() {
        // a CSV saved by Excel on Windows: "Café; 5 €"
        assertThat(Text.decode("Café; 5 €".getBytes(Charset.forName("windows-1252")))).isEqualTo("Café; 5 €");
    }
}
