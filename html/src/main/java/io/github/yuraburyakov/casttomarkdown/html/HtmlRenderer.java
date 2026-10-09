package io.github.yuraburyakov.casttomarkdown.html;

import io.github.yuraburyakov.casttomarkdown.internal.Markdown;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/** Renders one HTML document; a new instance for every document. */
final class HtmlRenderer {

    /**
     * Page furniture that is never content: scripts, embedded media and form controls (not the form: ASP.NET wraps
     * the whole page in one), navigation, sidebars, hidden elements and the landmark roles of the same.
     */
    private static final String FURNITURE = "script, style, noscript, template, svg, canvas, iframe, object, embed,"
            + " img, picture, video, audio, map, input, select, textarea, button, nav, [hidden],"
            + " [aria-hidden=true], [role=navigation], [role=banner], [role=contentinfo], [role=complementary],"
            + " [role=search]";
    private static final Pattern HIDDEN_STYLE = Pattern.compile("(?i)display\\s*:\\s*none|visibility\\s*:\\s*hidden");
    /** A link that skips the navigation: "Skip to main content". */
    private static final Pattern PERMALINK = Pattern.compile("[¶§#🔗]");
    private static final Pattern SKIP_LINK = Pattern.compile("(?i)^skip\\b.*");
    /**
     * Elements that are text inside a block; every other element starts a new block, also the custom elements of
     * web apps ({@code <react-app>}, {@code <turbo-frame>} on GitHub) and tags this list does not know.
     */
    private static final Set<String> INLINE = Set.of("a", "abbr", "acronym", "b", "bdi", "bdo", "big", "br", "cite",
            "code", "data", "del", "dfn", "em", "font", "i", "ins", "kbd", "label", "mark", "nobr", "q", "rp", "rt",
            "ruby", "s", "samp", "small", "span", "strike", "strong", "sub", "sup", "time", "tt", "u", "var", "wbr");
    /** Inside a table cell these make the table a layout of the page, read as blocks, not a table of data. */
    private static final String LAYOUT = "table, p + p, ul, ol, dl, pre, blockquote, h1, h2, h3, h4, h5, h6";
    private static final Pattern HEADING = Pattern.compile("h([1-6])");
    /** Any white space, also no-break ones: gov.uk puts U+202F around an abbreviation. */
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");
    /** A line break, then only white space up to the next one. */
    private static final Pattern BLANK_LINE = Pattern.compile("\\n[\\s\\p{Z}&&[^\\n]]*\\n");
    /** The start of something CommonMark reads as raw HTML: a tag, a closing tag, a comment, an instruction. */
    private static final Pattern TAG_START = Pattern.compile("<(?=[A-Za-z/!?]|$)");
    private static final Pattern BACKTICKS = Pattern.compile("`+");
    /** The language of a code block in a class: {@code language-x}, {@code lang-x}, {@code brush: x} (MDN), {@code highlight-x} (Sphinx). */
    private static final Pattern LANGUAGE = Pattern.compile("(?:^|\\s)(?:lang(?:uage)?-|highlight-|brush:\\s*)([\\w+#-]+)");
    private static final Pattern SAFE_BASE = Pattern.compile("(?i)https?://.+");
    /** Spaces per list nesting level, as in the DOCX module. */
    private static final String LIST_INDENT = "    ";
    /**
     * Deeper elements are read as plain text: a hostile page can nest elements far deeper than the stack allows
     * recursion.
     */
    private static final int MAX_DEPTH = 200;
    /** A language switcher has at least this many languages; one link to another language is content. */
    private static final int MIN_LANGUAGES = 3;
    private static final Pattern SCOPE = Pattern.compile("(?:source|text)-([^-]+).*");
    /** Lists and quotes deeper than this are rendered as plain blocks. */
    private static final int MAX_NESTING = 10;
    /** A Markdown table has at most this many cells, padding included; a larger one is read as blocks. */
    private static final int MAX_TABLE_CELLS = 100_000;
    /** A colspan larger than this is taken as this. */
    private static final int MAX_SPAN = 50;

    private final Document document;
    /** Lists and quotes around the block being rendered. */
    private int nesting;
    private final URI base;

    HtmlRenderer(Document document) {
        this.document = document;
        this.base = base(document);
    }

    String render() {
        removeFurniture();
        Element body = document.body();
        Elements mains = body.select("main, [role=main]");
        Element root = mains.size() == 1 ? mains.first() : body;
        List<String> blocks = new ArrayList<>();
        String title = escapeTags(SPACES.matcher(document.title()).replaceAll(" ").strip());
        if (root.selectFirst("h1") == null && !title.isEmpty()) {
            blocks.add("# " + Markdown.escape(title));
        }
        blocks.addAll(blocks(root, 0));
        withoutEmptyHeadings(blocks);
        return String.join("\n\n", blocks);
    }

    /**
     * Leaves out a heading below level 1 with nothing under it: the next block is a heading of a higher level, or
     * there is none (gov.uk "Related content" over navigation that is gone). Two headings of one level stay: gov.uk
     * sets the title of a guide and of its part so.
     */
    private static void withoutEmptyHeadings(List<String> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            int level = headingLevel(blocks.get(i));
            // a level 1 heading is the title of the document, kept also alone
            if (level > 1 && (i == blocks.size() - 1
                    || headingLevel(blocks.get(i + 1)) > 0 && headingLevel(blocks.get(i + 1)) < level)) {
                blocks.remove(i);
            }
        }
    }

    /** 1 for {@code # }, ... 6; 0 for a block that is no heading. */
    private static int headingLevel(String block) {
        int level = 0;
        while (level < block.length() && block.charAt(level) == '#') {
            level++;
        }
        return level > 0 && level <= 6 && level < block.length() && block.charAt(level) == ' ' ? level : 0;
    }

    /**
     * The address the page gives itself, for relative links: {@code <base href>}, the canonical link or
     * {@code og:url}, the first that is an absolute http(s) address; {@code null} for none.
     */
    private static URI base(Document document) {
        List<String> candidates = new ArrayList<>();
        for (Element base : document.select("base[href]")) {
            candidates.add(base.attr("href"));
        }
        for (Element canonical : document.select("link[rel=canonical][href]")) {
            candidates.add(canonical.attr("href"));
        }
        for (Element og : document.select("meta[property=og:url][content]")) {
            candidates.add(og.attr("content"));
        }
        for (String candidate : candidates) {
            if (SAFE_BASE.matcher(candidate.strip()).matches()) {
                try {
                    return new URI(candidate.strip());
                } catch (java.net.URISyntaxException e) {
                    // the next one
                }
            }
        }
        return null;
    }

    private void removeFurniture() {
        Element body = document.body();
        body.select(FURNITURE).remove();
        for (Element styled : body.select("[style]")) {
            if (HIDDEN_STYLE.matcher(styled.attr("style")).find()) {
                styled.remove();
            }
        }
        // the header, footer and sidebar of the page, not those of an article or section (footnotes of Sphinx)
        // one walk down the tree, counting the sections around: asking each element for its parents is quadratic
        // on a hostile page of thousands of nested headers
        List<Element> pageParts = new ArrayList<>();
        int[] sections = {0};
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof Element element) {
                    if (isSection(element)) {
                        sections[0]++;
                    } else if (sections[0] == 0 && (element.nameIs("header") || element.nameIs("footer")
                            || element.nameIs("aside"))) {
                        pageParts.add(element);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && isSection(element)) {
                    sections[0]--;
                }
            }
        }, body);
        pageParts.forEach(Element::remove);
        // MediaWiki: "[edit]" at every heading
        body.select("span.mw-editsection").remove();
        // a language switcher: a list of links, each to the page in another language (hreflang)
        for (Element list : body.select("ul, ol")) {
            Elements items = list.children();
            // each item only the link, so that nested lists are not read again for each level
            if (items.size() >= MIN_LANGUAGES && items.stream().allMatch(item -> item.childrenSize() == 1
                    && item.child(0).nameIs("a") && item.child(0).hasAttr("hreflang") && item.ownText().isBlank())) {
                list.remove();
            }
        }
        // a permalink of a heading: "¶" (Sphinx, mkdocs), "§", "#"
        for (Element link : body.select("a[href*=#]")) {
            if (PERMALINK.matcher(link.text().strip()).matches()) {
                link.remove();
            }
        }
        for (Element link : body.select("a[href^=#]")) {
            if (SKIP_LINK.matcher(link.text().strip()).matches()) {
                link.remove();
            }
        }
    }

    /** The Markdown blocks of the element's content. */
    private List<String> blocks(Element element, int depth) {
        List<String> blocks = new ArrayList<>();
        if (depth > MAX_DEPTH) {
            paragraph(blocks, element.text());
            return blocks;
        }
        StringBuilder inline = new StringBuilder();
        for (Node node : element.childNodes()) {
            if (node instanceof Element child && !INLINE.contains(child.normalName())) {
                paragraph(blocks, inline.toString());
                inline.setLength(0);
                block(blocks, child, depth + 1);
            } else {
                inline(inline, node, depth + 1);
            }
        }
        paragraph(blocks, inline.toString());
        return blocks;
    }

    private void block(List<String> blocks, Element element, int depth) {
        String name = element.normalName();
        Matcher heading = HEADING.matcher(name);
        if (heading.matches()) {
            String text = oneLine(inlineText(element, depth));
            if (!text.isEmpty()) {
                blocks.add("#".repeat(Integer.parseInt(heading.group(1))) + " " + Markdown.escape(text));
            }
        } else if ((name.equals("ul") || name.equals("ol") || name.equals("menu") || name.equals("blockquote"))
                && nesting >= MAX_NESTING) {
            // deeper lists and quotes are plain blocks: each level would indent all lines below it again
            blocks.addAll(blocks(element, depth));
        } else if (name.equals("ul") || name.equals("ol") || name.equals("menu")) {
            nesting++;
            String list = list(element, depth);
            nesting--;
            if (!list.isEmpty()) {
                blocks.add(list);
            }
        } else if (name.equals("table")) {
            table(blocks, element, depth);
        } else if (name.equals("pre")) {
            blocks.add(codeBlock(element));
        } else if (name.equals("blockquote")) {
            nesting++;
            String quoted = String.join("\n\n", blocks(element, depth));
            nesting--;
            if (!quoted.isEmpty()) {
                blocks.add(quoted.lines().map(line -> line.isEmpty() ? ">" : "> " + line).collect(Collectors.joining("\n")));
            }
        } else if (!name.equals("hr")) {
            blocks.addAll(blocks(element, depth));
        }
    }

    /** A paragraph of inline text; a line break ({@code <br>}) stays one. */
    private static void paragraph(List<String> blocks, String inline) {
        // two line breaks in a row end a paragraph (paulgraham.com writes paragraphs so)
        for (String part : BLANK_LINE.split(inline)) {
            String text = part.lines()
                    .map(line -> SPACES.matcher(line).replaceAll(" ").strip())
                    .filter(line -> !line.isEmpty())
                    .map(Markdown::escape)
                    .collect(Collectors.joining("\n"));
            if (!text.isEmpty()) {
                blocks.add(text);
            }
        }
    }

    private void inline(StringBuilder out, Node node, int depth) {
        if (node instanceof TextNode text) {
            out.append(escapeTags(SPACES.matcher(text.getWholeText()).replaceAll(" ")));
        } else if (node instanceof Element element) {
            if (depth > MAX_DEPTH) {
                out.append(element.text());
                return;
            }
            // links right next to another element are laid out apart by CSS (GitHub topics): a space between them
            // when words meet; a footnote "[<a>1</a>]" of Sphinx, with its brackets in elements, stays whole
            if (element.previousSibling() instanceof Element previous && (element.nameIs("a") || previous.nameIs("a"))
                    && !out.isEmpty() && !Character.isWhitespace(out.charAt(out.length() - 1))) {
                String before = previous.text();
                String text = element.text();
                if (!before.isEmpty() && Character.isLetterOrDigit(before.charAt(before.length() - 1))
                        && !text.isEmpty() && Character.isLetterOrDigit(text.charAt(0))) {
                    out.append(' ');
                }
            }
            switch (element.normalName()) {
                case "br" -> out.append('\n');
                // spaces at the ends of the text stay, outside the brackets: <a>A.1. </a><a>Example</a>
                case "a" -> out.append(Markdown.link(SPACES.matcher(inlineText(element, depth)).replaceAll(" "),
                        url(element.attr("href")), true));
                case "code", "kbd", "samp" -> out.append(codeSpan(element.text()));
                case "sup", "sub" -> out.append(script(inlineText(element, depth), element.nameIs("sup")));
                default -> {
                    for (Node child : element.childNodes()) {
                        inline(out, child, depth + 1);
                    }
                }
            }
        }
    }

    private String inlineText(Element element, int depth) {
        StringBuilder text = new StringBuilder();
        for (Node child : element.childNodes()) {
            inline(text, child, depth + 1);
        }
        return text.toString();
    }

    /**
     * Text of the page that a CommonMark renderer would read as HTML and hide: {@code List<E>} of Javadoc,
     * "the &lt;caption&gt; element" of MDN. A {@code <} before a letter, {@code /}, {@code !} or {@code ?} is
     * escaped, and one at the end of a text node, as the next node may go on with a letter (Javadoc writes
     * {@code Iterator&lt;<a>E</a>&gt;}); {@code a < b} and {@code x<5} stay. Code and the autolinks written here
     * are not text of the page.
     */
    private static String escapeTags(String text) {
        return TAG_START.matcher(text).replaceAll("\\\\<");
    }

    private static String oneLine(String text) {
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    /** The absolute target of a link; {@code null} for an anchor on the page or a relative link without a base. */
    private String url(String href) {
        String target = href.strip();
        if (target.isEmpty() || target.startsWith("#")) {
            return null;
        }
        try {
            URI uri = new URI(target);
            if (uri.isAbsolute()) {
                return target;
            }
            return base == null ? null : base.resolve(uri).toString();
        } catch (java.net.URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /** List items, each nested list four spaces deeper; a numbered list starts at its {@code start}. */
    private String list(Element list, int depth) {
        boolean numbered = list.normalName().equals("ol");
        int number = numbered ? start(list) : 0;
        List<String> items = new ArrayList<>();
        for (Element item : list.children()) {
            List<String> content = item.normalName().equals("li") ? blocks(item, depth + 1) : List.of();
            if (content.isEmpty()) {
                continue;
            }
            String marker = numbered ? number++ + "." : "-";
            String continuation = " ".repeat(marker.length() + 1);
            StringBuilder text = new StringBuilder(marker).append(' ')
                    .append(content.get(0).replace("\n", "\n" + continuation));
            for (String block : content.subList(1, content.size())) {
                // four spaces continue any marker up to "99." and nest a list under it
                text.append('\n').append(block.lines().map(line -> LIST_INDENT + line).collect(Collectors.joining("\n")));
            }
            items.add(text.toString());
        }
        return String.join("\n", items);
    }

    /** An article, main or section: its header, footer and aside are its own, not the page's. */
    private static boolean isSection(Element element) {
        return element.nameIs("article") || element.nameIs("main") || element.nameIs("section")
                || "main".equals(element.attr("role"));
    }

    private static int start(Element list) {
        try {
            return Integer.parseInt(list.attr("start").strip());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** A table of data as a Markdown table; a table that lays out the page as its blocks. */
    private void table(List<String> blocks, Element table, int depth) {
        List<Element> rows = new ArrayList<>();
        for (Element child : table.children()) {
            if (child.nameIs("tr")) {
                rows.add(child);
            } else if (child.nameIs("thead") || child.nameIs("tbody") || child.nameIs("tfoot")) {
                rows.addAll(child.children().stream().filter(row -> row.nameIs("tr")).toList());
            }
        }
        List<Element> cells = rows.stream().flatMap(row -> row.children().stream())
                .filter(cell -> cell.nameIs("td") || cell.nameIs("th")).toList();
        // a layout: blocks in the cells, or mostly empty cells that held spacer images (Hacker News)
        long empty = cells.stream().filter(cell -> oneLine(cell.text()).isEmpty()).count();
        if (2 * empty > cells.size() || cells.stream().anyMatch(cell -> cell.selectFirst(LAYOUT) != null)) {
            for (Element cell : cells) {
                blocks.addAll(blocks(cell, depth + 1));
            }
            return;
        }
        Element caption = table.selectFirst("> caption");
        if (caption != null) {
            paragraph(blocks, inlineText(caption, depth));
        }
        List<List<String>> grid = new ArrayList<>();
        int columns = 0;
        for (Element row : rows) {
            List<String> line = new ArrayList<>();
            for (Element cell : row.children()) {
                if (!cell.nameIs("td") && !cell.nameIs("th")) {
                    continue;
                }
                line.add(Markdown.tableCell(oneLine(inlineText(cell, depth))));
                for (int span = Math.min(span(cell), MAX_SPAN); span > 1; span--) {
                    line.add("");
                }
            }
            if (!line.isEmpty()) {
                columns = Math.max(columns, line.size());
                grid.add(line);
            }
        }
        if (grid.isEmpty()) {
            return;
        }
        if ((long) columns * grid.size() > MAX_TABLE_CELLS) {
            // padding every row to the widest one would blow up a hostile table; its cells are read as blocks
            for (Element cell : cells) {
                blocks.addAll(blocks(cell, depth + 1));
            }
            return;
        }
        StringBuilder markdown = new StringBuilder();
        for (int r = 0; r < grid.size(); r++) {
            List<String> line = grid.get(r);
            while (line.size() < columns) {
                line.add("");
            }
            markdown.append("| ").append(String.join(" | ", line)).append(" |\n");
            if (r == 0) {
                markdown.append('|').append(" --- |".repeat(columns)).append('\n');
            }
        }
        blocks.add(markdown.toString().stripTrailing());
    }

    private static int span(Element cell) {
        try {
            return Math.max(1, Integer.parseInt(cell.attr("colspan").strip()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** A fenced code block, the fence longer than any run of backticks in the code. */
    private static String codeBlock(Element pre) {
        String code = pre.wholeText().replaceFirst("^\\r?\\n", "").stripTrailing();
        Element first = pre.selectFirst("code");
        // Sphinx puts the language two wrappers above the <pre>
        Element parent = pre.parent();
        Element grandparent = parent == null ? null : parent.parent();
        Matcher language = LANGUAGE.matcher(String.join(" ", pre.className(), first == null ? "" : first.className(),
                parent == null ? "" : parent.className(), grandparent == null ? "" : grandparent.className()));
        String fence = "`".repeat(Math.max(3, longestBackticks(code) + 1));
        String name = language.find() ? language.group(1).toLowerCase(Locale.ROOT) : "";
        // GitHub names the grammar scope: highlight-source-java, highlight-text-html-basic
        Matcher scope = SCOPE.matcher(name);
        return fence + (scope.matches() ? scope.group(1) : name) + "\n" + code + "\n" + fence;
    }

    private static String codeSpan(String code) {
        String text = SPACES.matcher(code).replaceAll(" ").strip();
        if (text.isEmpty()) {
            return "";
        }
        String fence = "`".repeat(longestBackticks(text) + 1);
        String pad = text.startsWith("`") || text.endsWith("`") ? " " : "";
        return fence + pad + text + pad + fence;
    }

    /**
     * Text of {@code <sup>} or {@code <sub>} in Unicode superscript or subscript characters when it has them all
     * ({@code 0<sub>16</sub>} is {@code 0₁₆}, {@code 10<sup>-3</sup>} is {@code 10⁻³}), as the PDF module writes
     * raised digits; else the text as it is (a reference {@code [2]}).
     */
    private static String script(String text, boolean superscript) {
        String plain = text.strip().replace('−', '-');
        String from = superscript ? "0123456789+-=()ni" : "0123456789+-=()";
        String to = superscript ? "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ" : "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎";
        StringBuilder out = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            int at = from.indexOf(plain.charAt(i));
            if (at < 0) {
                return text;
            }
            out.append(to.charAt(at));
        }
        return out.toString();
    }

    private static int longestBackticks(String text) {
        int longest = 0;
        Matcher run = BACKTICKS.matcher(text);
        while (run.find()) {
            longest = Math.max(longest, run.group().length());
        }
        return longest;
    }
}
