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
    private static final int MAX_LENGTH = 200;
    private static final int MAX_LEVEL = 6;
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

    /** Arabic section number; group 1 is the top-level number. */
    private static final Pattern TOP_NUMBER = Pattern.compile("^(\\d{1,2})(?:\\.\\d{1,2})*\\.?(?=[\\s\\u2014:])");

    private Headings() {
    }

    static boolean startsWithSectionNumber(String text) {
        return SECTION_NUMBER.matcher(text).find();
    }

    /** Heading level ({@code 1} for {@code #}) of each paragraph, {@code 0} for a paragraph that is not a heading. */
    static int[] levels(List<List<Line>> paragraphs) {
        return levels(paragraphs, find(paragraphs));
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
        int body = bodySizeKey(paragraphs);
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
                    && (size > body || boldNumbered)
                    && paragraph.size() <= (size >= TITLE_SIZE_RATIO * body ? MAX_TITLE_LINES : MAX_LINES)
                    && text.length() <= MAX_LENGTH
                    && (numbered || WORD.matcher(text).find())
                    && !DOT_LEADER.matcher(text).find();
            // A heading that ends a page relies on PageFurniture: if a running header or footer is not
            // recognized, it follows the heading and the heading is missed.
            boolean followedByText = i == paragraphs.size() - 1
                    || headings[i + 1]
                    || paragraphs.get(i + 1).get(0).sizeKey() == body;
            headings[i] = candidate && followedByText;
        }
        dropNumbersOutOfSequence(paragraphs, headings);
        return headings;
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

    /**
     * Numbered heading: level = depth of the number + 1 ({@code 2} is {@code ##}, {@code 2.1} is {@code ###});
     * {@code #} is left for the document title. A heading without a number takes the level of numbered
     * headings in the same font (e.g. "Abstract" next to "1. Introduction"); otherwise headings in other
     * fonts are ranked by size, largest first.
     */
    private static int[] levels(List<List<Line>> paragraphs, boolean[] headings) {
        int[] levels = new int[paragraphs.size()];
        Map<Integer, Integer> numberedLevelByStyle = new HashMap<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            int depth = headings[i] ? sectionDepth(text(paragraphs.get(i))) : 0;
            if (depth > 0) {
                levels[i] = Math.min(MAX_LEVEL, depth + 1);
                numberedLevelByStyle.merge(paragraphs.get(i).get(0).styleKey(), levels[i], Math::min);
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
        return withoutSkippedLevels(levels);
    }

    /**
     * A heading goes at most one level deeper than the heading before it: fonts are ranked over the whole
     * document, and a font that never meets the deeper one in the same section left a gap
     * ({@code ##} then {@code ####} in an InDesign PDF). The first heading keeps its level.
     */
    private static int[] withoutSkippedLevels(int[] levels) {
        int previous = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] > 0) {
                if (previous > 0) {
                    levels[i] = Math.min(levels[i], previous + 1);
                }
                previous = levels[i];
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
