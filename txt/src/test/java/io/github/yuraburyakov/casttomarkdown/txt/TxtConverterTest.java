package io.github.yuraburyakov.casttomarkdown.txt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuraburyakov.casttomarkdown.CastToMarkdown;
import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TxtConverterTest {

    private final TxtConverter converter = new TxtConverter();

    @TempDir
    Path dir;

    @Test
    void linesAndParagraphsStay() {
        assertThat(convert("First line\r\nsecond line\r\n\r\n\r\nNext paragraph.\n"))
                .isEqualTo("First line\nsecond line\n\nNext paragraph.\n");
    }

    @Test
    void blockSyntaxAtLineStartIsEscaped() {
        assertThat(convert("# not a heading\n> not a quote\n```\n---\nkept - inside")).isEqualTo(
                "\\# not a heading\n\\> not a quote\n\\```\n\\---\nkept - inside\n");
    }

    @Test
    void indentationAndTrailingSpaces() {
        // the indentation of a text table or of code stays; spaces at the end of a line go
        assertThat(convert("Table:\n\n    a   b\n    1   2   \n")).isEqualTo("Table:\n\n    a   b\n    1   2\n");
    }

    @Test
    void emptyFileIsEmpty() {
        assertThat(convert("")).isEmpty();
        assertThat(convert("\n \n")).isEmpty();
    }

    @Test
    void charsetsAndExtension() throws IOException {
        Path file = dir.resolve("notes.TXT");
        Files.write(file, "Café 5 €".getBytes(Charset.forName("windows-1252")));
        assertThat(CastToMarkdown.create().convert(file).markdown()).isEqualTo("Café 5 €\n");

        boolean[] closed = {false};
        InputStream stream = new ByteArrayInputStream("Grüße".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        assertThat(CastToMarkdown.create().convert(stream, "a.txt").markdown()).isEqualTo("Grüße\n");
        assertThat(closed[0]).isFalse();
    }

    @Test
    void unreadableFileIsAConversionError() {
        assertThatThrownBy(() -> converter.convert(dir.resolve("missing.txt"))).isInstanceOf(DocumentConversionException.class);
    }

    private String convert(String text) {
        return converter.convert(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "a.txt");
    }
}
