package io.github.yuraburyakov.casttomarkdown.pdf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Finds headings among paragraphs and their Markdown level. */
final class Headings {

    /** Longer paragraphs are not headings. */
    private static final int MAX_LINES = 2;
    /** A document title is often longer: up to this many lines when the font is much larger than the body font. */
    private static final int MAX_TITLE_LINES = 4;
    private static final float TITLE_SIZE_RATIO = 1.5f;
    /** The first paragraph of a document is a title also in a font this much larger than the body. */
    private static final float FIRST_TITLE_SIZE_RATIO = 1.25f;
    /** Lines whose centres differ by at most this share of the font size are centred on each other. */
    private static final float CENTRED = 0.1f;
    /** Lines of a centred heading are at most this many characters long. */
    private static final int SHORT_LINE = 40;
    private static final int MAX_LENGTH = 200;
    private static final int MAX_LEVEL = 6;
    /** Words of a heading that only the structure tree of a tagged PDF makes one (see {@link #taggedLevels}). */
    static final int MAX_TAGGED_WORDS = 12;
    /** A caption, "Figure 4-1 Digital Identity Model" (NIST tags it {@code H1}). */
    private static final Pattern CAPTION = Pattern.compile("^(Figure|Fig\\.|Table)\\s*\\d+");
    /** Bold text may be up to this much smaller than the body font and still be a numbered heading. */
    private static final float BOLD_MAX_SIZE_BELOW_BODY = 1.5f;
    /** A heading without a section number has at least one real word; rotated or scattered text gives only fragments. */
    private static final Pattern WORD = Pattern.compile("\\p{L}{3}");
    /** Table of contents entries: dot leaders between the title and the page number. */
    private static final Pattern DOT_LEADER = Pattern.compile("\\.{4,}|(\\. ){3,}");
    /**
     * Section number at the start of a heading: {@code 2}, {@code 2.1.}, {@code A.}, {@code A.1},
     * {@code Appendix A}, {@code \u00a7 1} (statutes, laws). Groups 1 to 3 hold the {@code .N} parts after the first number.
     */
    private static final Pattern SECTION_NUMBER = Pattern.compile("^(?:Appendix\\s+[A-Z]"
            + "|\\d{1,2}((?:\\.\\d{1,2})*)\\.?"
            + "|[A-Z]((?:\\.\\d{1,2})+)\\.?"
            + "|[A-Z]\\."
            + "|\u00a7\\s*\\d{1,3}((?:\\.\\d{1,2})*)\\.?)"
            + "(?=[\\s\u2014:])");

    /** The heading of a paper's abstract. */
    private static final Pattern ABSTRACT = Pattern.compile("(?i)abstract[.:]?");
    /** The abstract is on one of the first this many pages. */
    private static final int ABSTRACT_PAGES = 2;
    /** A date alone: "1 October 2026", "October 1, 2026", "May 2024", "2026-10-01". */
    private static final Pattern DATE = Pattern.compile("(?i)(?:\\d{1,2}(?:st|nd|rd|th)?\\s+)?"
            + "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?(?:\\s+\\d{1,2}(?:st|nd|rd|th)?,?)?\\s+\\d{4}"
            + "|\\d{4}-\\d{2}-\\d{2}");
    /** An e-mail address, also the {@code {kahe, v-xiangz}@microsoft.com} of a paper's authors. */
    private static final Pattern EMAIL = Pattern.compile("[\\w.}-]@[\\w-]+\\.[\\w.]+");

    /** Text and a page number after it; group 1 is the text. */
    private static final Pattern PAGE_NUMBER_AT_END = Pattern.compile("(.*\\S)\\s+\\d{1,4}");

    /** Arabic section number; group 1 is the top-level number. */
    private static final Pattern TOP_NUMBER = Pattern.compile("^(\\d{1,2})(?:\\.\\d{1,2})*\\.?(?=[\\s\\u2014:])");

    private Headings() {
    }

    static boolean startsWithSectionNumber(String text) {
        return SECTION_NUMBER.matcher(text).find();
    }

    /** Heading level ({@code 1} for {@code #}) of each paragraph, {@code 0} for a paragraph that is not a heading. */
    static int[] levels(List<List<Line>> paragraphs) {
        boolean[] headings = find(paragraphs);
        return levels(paragraphs, headings, taggedLevels(paragraphs, headings));
    }

    /**
     * The level of each paragraph that only the structure tree of a tagged PDF makes a heading, else 0: all its
     * lines are in a heading element ({@code H1} to {@code H6}, see {@link Line#heading()}) and it reads like
     * one: at most {@link #MAX_TAGGED_WORDS} words, no full stop at the end, no caption. Typst sets the entries of
     * API documentation as headings in the font of the text. Generators also tag other text as headings: InDesign
     * body text without a paragraph style ({@code H2}), Word a sentence (other-c1) or a caption (NIST). Headings
     * the fonts find keep the level of their font.
     */
    private static int[] taggedLevels(List<List<Line>> paragraphs, boolean[] headings) {
        int[] levels = new int[paragraphs.size()];
        for (int i = 0; i < paragraphs.size(); i++) {
            List<Line> paragraph = paragraphs.get(i);
            String text = text(paragraph);
            if (!headings[i] && paragraph.stream().allMatch(line -> line.heading() > 0)
                    && paragraph.size() <= MAX_LINES && text.split("\\s+").length <= MAX_TAGGED_WORDS
                    && !text.endsWith(".") && !CAPTION.matcher(text).find()) {
                levels[i] = paragraph.get(0).heading();
            }
        }
        return levels;
    }

    /**
     * "Abstract" alone on a line of the first pages, or bold (NIST, page 6): arXiv papers set it in the small font of the abstract
     * (1404.7828, 1712.01208), BERT one size above it, NIST in bold body size; the rules for headings by font
     * size miss them.
     */
    private static boolean isAbstract(List<Line> paragraph) {
        Line line = paragraph.get(0);
        return paragraph.size() == 1 && !line.isTable() && (line.page() <= ABSTRACT_PAGES || line.bold())
                && ABSTRACT.matcher(text(paragraph)).matches();
    }

    /**
     * Whether the lines are centred on one another and do not all start at one left edge, like a title of
     * several short lines ("Appendix for “BERT: Pre-training of” ...", arXiv 1810.04805); a justified paragraph
     * has one left edge, and a centred notice has long lines (the permission note atop arXiv 1706.03762).
     */
    private static boolean centred(List<Line> paragraph) {
        Line first = paragraph.get(0);
        float tolerance = CENTRED * first.fontSize();
        boolean shifted = false;
        for (Line line : paragraph) {
            if (line.width() <= 0 || line.text().strip().length() > SHORT_LINE
                    || Math.abs(line.x() + line.width() / 2 - (first.x() + first.width() / 2)) > tolerance) {
                return false;
            }
            shifted |= Math.abs(line.x() - first.x()) > tolerance;
        }
        return paragraph.size() > 1 && shifted;
    }

    /** Lines of a heading joined into one line. */
    static String text(List<Line> paragraph) {
        return paragraph.stream().map(line -> line.text().strip()).collect(Collectors.joining(" "));
    }

    /**
     * A heading is a short paragraph followed by body text or another heading, either in a font larger
     * than the body font (the font of most characters), or bold, about body size and starting with
     * a section number. Bold body-size text without a number is not a heading: in real documents that is
     * mostly glossary terms, table headers and emphasized words. "Followed by body text" drops large
     * text inside figures, which is followed by small figure text.
     */
    private static boolean[] find(List<List<Line>> paragraphs) {
        int primaryBody = bodySizeKey(paragraphs);
        java.util.Set<Integer> bodySizes = bodySizeKeys(paragraphs);
        int body = bodySizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        boolean[] headings = new boolean[paragraphs.size()];
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            List<Line> paragraph = paragraphs.get(i);
            String text = text(paragraph);
            int size = paragraph.get(0).sizeKey();
            boolean numbered = startsWithSectionNumber(text);
            boolean boldNumbered = numbered
                    && paragraph.stream().allMatch(Line::bold)
                    && size >= body - BOLD_MAX_SIZE_BELOW_BODY * 2;
            // ponytail: a bold numbered list item whose number still fits the section sequence ("2. Foo" right
            // after "1. Introduction") passes as a heading; out-of-sequence ones are dropped below.
            boolean candidate = !paragraph.get(0).isTable()
                    && (size > body || boldNumbered || numbered && size > primaryBody)
                    && paragraph.size() <= (size >= TITLE_SIZE_RATIO * body || centred(paragraph) ? MAX_TITLE_LINES : MAX_LINES)
                    && text.length() <= MAX_LENGTH
                    && (numbered || WORD.matcher(text).find())
                    && !DOT_LEADER.matcher(text).find();
            // A heading that ends a page relies on PageFurniture: if a running header or footer is not
            // recognized, it follows the heading and the heading is missed.
            // a title on the first page may be followed by the authors in a font a little larger than the body
            // so may the first paragraph of the document in any font larger than the body (arXiv 1810.04805)
            boolean title = paragraph.get(0).page() == 1
                    && (size >= TITLE_SIZE_RATIO * body || i == 0 && size >= FIRST_TITLE_SIZE_RATIO * body);
            boolean followedByText = i == paragraphs.size() - 1
                    || headings[i + 1]
                    || title
                    || bodySizes.contains(paragraphs.get(i + 1).get(0).sizeKey());
            headings[i] = candidate && followedByText;
        }
        dropContentsEntries(paragraphs, headings);
        dropNumbersOutOfSequence(paragraphs, headings);
        dropDatesAndAuthors(paragraphs, headings);
        // after the others, so that it does not make the authors above it headings followed by a heading
        for (int i = 0; i < paragraphs.size(); i++) {
            if (isAbstract(paragraphs.get(i))) {
                headings[i] = true;
            }
        }
        return headings;
    }

    /**
     * An entry of a table of contents without dot leaders is the text of a later paragraph and a page number
     * ("1 Introduction to Deep Learning (DL) in Neural Networks (NNs) 4", arXiv 1404.7828, bold like the
     * headings); left as a heading, its numbers would also drop the real headings as out of sequence.
     */
    private static void dropContentsEntries(List<List<Line>> paragraphs, boolean[] headings) {
        java.util.Set<String> later = new java.util.HashSet<>();
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            String text = text(paragraphs.get(i));
            Matcher entry = PAGE_NUMBER_AT_END.matcher(text);
            if (headings[i] && entry.matches() && later.contains(entry.group(1))) {
                headings[i] = false;
            }
            later.add(text);
        }
    }

    /**
     * Large text of a title page that is not a heading: a date alone ("1 October 2026" under a gov.uk title)
     * and the authors right after the title of the first page when an e-mail address follows them
     * ("Kaiming He Xiangyu Zhang ... Microsoft Research" in arXiv 1512.03385).
     */
    private static void dropDatesAndAuthors(List<List<Line>> paragraphs, boolean[] headings) {
        for (int i = 0; i < paragraphs.size(); i++) {
            if (headings[i] && DATE.matcher(text(paragraphs.get(i))).matches()) {
                headings[i] = false;
            }
        }
        int title = 0;
        while (title < headings.length && !headings[title]) {
            title++;
        }
        int authors = title + 1;
        if (authors + 1 < paragraphs.size() && paragraphs.get(title).get(0).page() == 1 && headings[authors]
                && !headings[authors + 1] && EMAIL.matcher(text(paragraphs.get(authors + 1))).find()) {
            headings[authors] = false;
        }
    }

    /**
     * A bold numbered list item looks like a heading ("1. OPTIONAL" inside section 5.7 of RFC 9562), but
     * top-level numbers of real headings do not go down: a {@code 1.} after {@code 5.7} is not a heading.
     * The same number again is allowed: gov.uk guidance numbers the steps "1. Obtain", "2. Check" inside
     * its section "1. Conducting a check". A heading without a number ("Appendix", "Part II") starts the
     * count again.
     */
    private static void dropNumbersOutOfSequence(List<List<Line>> paragraphs, boolean[] headings) {
        int lastTop = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            if (!headings[i]) {
                continue;
            }
            String text = text(paragraphs.get(i));
            Matcher number = TOP_NUMBER.matcher(text);
            if (!number.find()) {
                lastTop = startsWithSectionNumber(text) ? lastTop : 0;
                continue;
            }
            int top = Integer.parseInt(number.group(1));
            if (top < lastTop) {
                headings[i] = false;
            } else {
                lastTop = top;
            }
        }
    }

    /** Size key of the font with the most characters. */
    private static int bodySizeKey(List<List<Line>> paragraphs) {
        Map<Integer, Integer> charsBySize = new HashMap<>();
        for (List<Line> paragraph : paragraphs) {
            for (Line line : paragraph) {
                charsBySize.merge(line.sizeKey(), line.text().strip().length(), Integer::sum);
            }
        }
        return charsBySize.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    /** A second body font needs substantial repeated evidence, not merely a nearby size. */
    private static java.util.Set<Integer> bodySizeKeys(List<List<Line>> paragraphs) {
        int primary = bodySizeKey(paragraphs);
        Map<Integer, Integer> counts = new HashMap<>();
        Map<Integer, Integer> weights = new HashMap<>();
        for (List<Line> paragraph : paragraphs) {
            for (Line line : paragraph) {
                if (!line.isTable() && !line.rotated() && !line.bold() && line.text().strip().length() >= 40) {
                    counts.merge(line.sizeKey(), 1, Integer::sum);
                    weights.merge(line.sizeKey(), line.text().strip().length(), Integer::sum);
                }
            }
        }
        java.util.Set<Integer> sizes = new java.util.HashSet<>();
        sizes.add(primary);
        int primaryWeight = weights.getOrDefault(primary, 0);
        if (counts.getOrDefault(primary, 0) >= 10) {
            for (var entry : weights.entrySet()) {
                int size = entry.getKey();
                // At most 1 pt above the primary, >=10 long plain lines, >=half its text weight.
                if (size > primary && size <= primary + 2 && counts.get(size) >= 10
                        && interleavings(paragraphs, primary, size) >= 10
                        && entry.getValue() >= primaryWeight * 0.5) {
                    sizes.add(size);
                }
            }
        }
        return sizes;
    }

    /** Distinguish mixed body typography from separate sections using different fonts. */
    private static int interleavings(List<List<Line>> paragraphs, int primary, int secondary) {
        List<Line> lines = paragraphs.stream().flatMap(List::stream).toList();
        int count = 0;
        for (int i = 1; i < lines.size(); i++) {
            Line a = lines.get(i - 1);
            Line b = lines.get(i);
            float size = Math.max(a.fontSize(), b.fontSize());
            boolean pair = a.sizeKey() == primary && b.sizeKey() == secondary
                    || a.sizeKey() == secondary && b.sizeKey() == primary;
            if (pair && a.page() == b.page() && !a.bold() && !b.bold()
                    && !a.rotated() && !b.rotated() && !a.isTable() && !b.isTable()
                    && a.text().strip().length() >= 40 && b.text().strip().length() >= 40
                    && b.y() > a.y() && b.y() - a.y() <= 1.5f * size
                    && Math.abs(a.x() - b.x()) <= 0.5f * size) {
                count++;
            }
        }
        return count;
    }

    /**
     * Numbered heading: level = depth of the number + 1 ({@code 2} is {@code ##}, {@code 2.1} is {@code ###});
     * {@code #} is left for the document title. A heading without a number takes the level of numbered
     * headings in the same font (e.g. "Abstract" next to "1. Introduction"); otherwise headings in other
     * fonts are ranked by size, largest first.
     */
    private static int[] levels(List<List<Line>> paragraphs, boolean[] headings, int[] tagged) {
        int[] levels = new int[paragraphs.size()];
        Map<Integer, Integer> numberedLevelByStyle = new HashMap<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            int depth = headings[i] ? sectionDepth(text(paragraphs.get(i))) : 0;
            if (depth > 0) {
                levels[i] = Math.min(MAX_LEVEL, depth + 1);
                numberedLevelByStyle.merge(paragraphs.get(i).get(0).styleKey(), levels[i], Math::min);
            }
        }
        for (int i = 0; i < paragraphs.size(); i++) {
            if (tagged[i] > 0) {
                levels[i] = tagged[i];
            }
        }
        List<Integer> otherStyles = new ArrayList<>(); // fonts of headings with no numbered heading, largest first
        for (int i = 0; i < paragraphs.size(); i++) {
            int style = paragraphs.get(i).get(0).styleKey();
            if (headings[i] && levels[i] == 0 && !numberedLevelByStyle.containsKey(style) && !otherStyles.contains(style)) {
                otherStyles.add(style);
            }
        }
        otherStyles.sort(Comparator.reverseOrder());
        for (int i = 0; i < paragraphs.size(); i++) {
            if (headings[i] && levels[i] == 0) {
                int style = paragraphs.get(i).get(0).styleKey();
                levels[i] = numberedLevelByStyle.getOrDefault(style,
                        Math.min(MAX_LEVEL, otherStyles.indexOf(style) + 1));
            }
        }
        titleOnTop(paragraphs, levels);
        return withoutSkippedLevels(levels);
    }

    /**
     * An unnumbered first heading on the first page is the document title, level 1, when no heading has that
     * level: word365-taxi.pdf sets its title in the font of the numbered sections, which are level 2.
     */
    private static void titleOnTop(List<List<Line>> paragraphs, int[] levels) {
        int first = -1;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == 1) {
                return;
            }
            if (levels[i] > 0 && first < 0) {
                first = i;
            }
        }
        if (first >= 0 && paragraphs.get(first).get(0).page() == 1 && !startsWithSectionNumber(text(paragraphs.get(first)))) {
            levels[first] = 1;
        }
    }

    /**
     * A heading goes at most one level deeper than the heading before it: fonts are ranked over the whole
     * document, and a font that never meets the deeper one in the same section left a gap
     * ({@code ##} then {@code ####} in an InDesign PDF). A heading of the level of the one before gets its level
     * too, and a higher one goes no deeper than it: two {@code H3} after an {@code H1} are both {@code ##}.
     * The first heading keeps its level.
     */
    private static int[] withoutSkippedLevels(int[] levels) {
        int previous = 0;
        int previousLevel = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] > 0) {
                int level = levels[i];
                if (previous > 0) {
                    levels[i] = level > previous ? Math.min(level, previousLevel + 1)
                            : level == previous ? previousLevel : Math.min(level, previousLevel);
                }
                previous = level;
                previousLevel = levels[i];
            }
        }
        return levels;
    }

    /** 0 when the text does not start with a section number, 1 for {@code 2} or {@code A.}, 2 for {@code 2.1}. */
    private static int sectionDepth(String text) {
        Matcher number = SECTION_NUMBER.matcher(text);
        if (!number.find()) {
            return 0;
        }
        String subsections = null;
        for (int group = 1; group <= number.groupCount() && subsections == null; group++) {
            subsections = number.group(group);
        }
        return 1 + (subsections == null ? 0 : (int) subsections.chars().filter(c -> c == '.').count());
    }
}
