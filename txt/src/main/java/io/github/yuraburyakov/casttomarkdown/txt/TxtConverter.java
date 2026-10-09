package io.github.yuraburyakov.casttomarkdown.txt;

import io.github.yuraburyakov.casttomarkdown.DocumentConversionException;
import io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter;
import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import io.github.yuraburyakov.casttomarkdown.internal.Text;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts plain text to Markdown: the lines as they are, a blank line between paragraphs, block syntax at the
 * start of a line escaped. Indentation stays (a table or code in the text keeps its columns). The charset comes
 * from a byte order mark, else UTF-8, else windows-1252.
 *
 * <p>Stateless and thread-safe.
 */
public final class TxtConverter implements DocumentConverter {

    /** Creates the converter; {@link java.util.ServiceLoader} calls it. */
    public TxtConverter() {
    }

    @Override
    public List<String> extensions() {
        return List.of("txt");
    }

    @Override
    public String convert(Path path) {
        try {
            return render(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read text file: " + path, e);
        }
    }

    @Override
    public String convert(InputStream input, String name) {
        try {
            return render(input.readAllBytes());
        } catch (IOException e) {
            throw new DocumentConversionException("Cannot read text file: " + name, e);
        }
    }

    private static String render(byte[] bytes) {
        return Markdown.normalize(Text.decode(bytes).lines()
                .map(line -> Markdown.escape(Markdown.escapeTags(line)))
                .collect(Collectors.joining("\n")));
    }
}
