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
        // a hostile page: 200 000 one-word lines on one baseline, each far from the others, none joins
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            float x = i * 20;
            lines.add(new Line(1, 792, x, 100, 12, false, false, "w", -1, 6, x, 100,
                    List.of(new Line.Word("w", x, x + 6))));
        }

        List<Line> result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LateText.insert(lines));

        assertThat(result).hasSize(200_000);
    }

    @Test
    void pageDrawnBottomUpTakesLinearTime() {
        // a hostile page: 200 000 lines, each drawn above all before it
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            float y = 400_000 - i * 2;
            lines.add(new Line(1, 792, 50, y, 1, false, false, "w", -1, 6, 50, y, List.of(new Line.Word("w", 50, 56))));
        }

        List<Line> result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LateText.insert(lines));

        assertThat(result).hasSize(200_000);
    }

    @Test
    void smallerKeywordFillsTheGapLeftForIt() {
        // RFC 9562 page 8: "MAY" in 12 pt drawn after its 13 pt line, in the gap between "UUIDs" and "be"
        Line line = line(13, "UUIDs be represented", new Line.Word("UUIDs", 65.9f, 95.7f),
                new Line.Word("be", 121.8f, 133.3f), new Line.Word("represented", 135.9f, 193.5f));
        Line keyword = line(12, "MAY", new Line.Word("MAY", 98.3f, 119.2f));

        assertThat(LateText.insert(List.of(line, keyword))).extracting(Line::text)
                .containsExactly("UUIDs MAY be represented");
    }

    @Test
    void textOfAnotherSizeAfterTheLineStaysApart() {
        // arXiv 1512.00567: a formula piece on the same baseline right after the line, 1 pt off in size
        Line line = line(10, "the bounded gradient", new Line.Word("the", 112.5f, 124.7f),
                new Line.Word("bounded", 127.3f, 161.7f), new Line.Word("gradient", 164.3f, 197.0f));
        Line piece = line(9, ", reduces", new Line.Word(",", 199.5f, 202.0f), new Line.Word("reduces", 204.7f, 235.2f));

        assertThat(LateText.insert(List.of(line, piece))).hasSize(2);
    }

    @Test
    void splitWordDoesNotLeaveItsSecondHalfBehind() {
        // arXiv 1512.00567: ", reduces the abil-" drawn after the denominator of a fraction, "ity" on the next line
        Line line = line(10, "the bounded gradient", new Line.Word("the", 112.5f, 124.7f),
                new Line.Word("bounded", 127.3f, 161.7f), new Line.Word("gradient", 164.3f, 197.0f));
        Line denominator = at(290, line(7, "dzk", new Line.Word("dzk", 200.0f, 208.0f)));
        Line late = line(10, ", reduces the abil-", new Line.Word(",", 199.5f, 202.0f),
                new Line.Word("reduces", 204.7f, 235.2f), new Line.Word("the", 237.9f, 250.1f),
                new Line.Word("abil-", 252.8f, 271.1f));
        Line next = at(293, line(10, "ity of the model", new Line.Word("ity", 50.1f, 60.0f)));

        assertThat(LateText.insert(List.of(line, denominator, late, next))).hasSize(4);
    }

    @Test
    void wholeLinesDrawnLaterGoBackBetweenTheLinesAroundThem() {
        // RFC 9562 page 36: the title lines of reference [X667] (a link) are drawn after the rest of the entry
        Line label = at(81.2f, line(10, "[X667] ITU-T, \"Information technology - Procedures for",
                new Line.Word("[X667]", 103.5f, 135f)));
        Line rest = at(122.0f, line(10, "components\", ISO/IEC 9834-8:2004", new Line.Word("components\",", 145.9f, 200f)));
        Line year = at(135.6f, line(10, "2004.", new Line.Word("2004.", 145.9f, 170f)));
        Line title1 = at(94.8f, line(10, "the operation of OSI Registration Authorities", new Line.Word("the", 145.9f, 160f)));
        Line title2 = at(108.4f, line(10, "Universally Unique Identifiers (UUIDs)", new Line.Word("Universally", 145.9f, 190f)));

        assertThat(LateText.insert(List.of(label, rest, year, title1, title2))).extracting(Line::y)
                .containsExactly(81.2f, 94.8f, 108.4f, 122.0f, 135.6f);
    }

    @Test
    void lateLineInAnotherColumnStaysWhereItIs() {
        // the right column comes after the left one; its lines start elsewhere, so they are not moved into it
        Line left1 = at(100, line(10, "left one", new Line.Word("left", 50, 70)));
        Line left2 = at(140, line(10, "left two", new Line.Word("left", 50, 70)));
        Line right = at(120, line(10, "right one", new Line.Word("right", 308, 330)));

        assertThat(LateText.insert(List.of(left1, left2, right))).containsExactly(left1, left2, right);
    }

    @Test
    void referenceLabelsDrawnBeforeTheHeadingGoToTheStartOfTheirEntries() {
        // RFC 9562 page 35: the labels are drawn first, then the heading, then the entries one font size right of them
        Line label1 = at(297.7f, line(10, "[C309]", new Line.Word("[C309]", 104.2f, 135.9f)));
        Line label2 = at(346.5f, line(10, "[C311]", new Line.Word("[C311]", 104.2f, 135.9f)));
        Line heading = at(273.9f, line(12, "9.1. Normative References", new Line.Word("9.1.", 65.9f, 85f),
                new Line.Word("Normative", 88f, 150f), new Line.Word("References", 153f, 220f)));
        Line entry1 = at(297.7f, line(10, "X/Open Company Limited", new Line.Word("X/Open", 145.9f, 180.7f),
                new Line.Word("Company", 183.3f, 228.4f), new Line.Word("Limited", 231f, 268.1f)));
        Line entry2 = at(346.5f, line(10, "The Open Group", new Line.Word("The", 145.9f, 163.7f),
                new Line.Word("Open", 166.3f, 191.7f), new Line.Word("Group", 194.3f, 224.4f)));

        assertThat(LateText.insert(List.of(label1, label2, heading, entry1, entry2))).extracting(Line::text)
                .containsExactly("9.1. Normative References", "[C309] X/Open Company Limited", "[C311] The Open Group");
    }

    @Test
    void wordsDrawnAfterThePunctuationBetweenThemJoinIt() {
        // RFC 9562 page 35: "and , , ," is drawn with the entry before it, the names and the title come later
        Line before = at(533.7f, line(10, "[rfc2119]>.", new Line.Word("[rfc2119]>.", 145.9f, 200f)));
        Line punctuation = at(555.3f, line(10, "and , , ,", new Line.Word("and", 218f, 236.2f),
                new Line.Word(",", 283.5f, 286f), new Line.Word(",", 453.9f, 456.4f), new Line.Word(",", 502.5f, 505f)));
        Line after = at(568.9f, line(10, "10.17487/RFC8141, April 2017", new Line.Word("10.17487/RFC8141,", 145.9f, 230f),
                new Line.Word("April", 232.6f, 255f), new Line.Word("2017", 257.6f, 280f)));
        Line words = at(555.3f, line(10, "Saint-Andre, P. J. Klensin \"Uniform Resource Names (URNs)\" RFC 8141 DOI",
                new Line.Word("Saint-Andre,", 145.9f, 205.4f), new Line.Word("P.", 208f, 215.4f),
                new Line.Word("J.", 238.8f, 244.9f), new Line.Word("Klensin", 247.5f, 283.5f),
                new Line.Word("\"Uniform", 288.6f, 333.1f), new Line.Word("Resource", 335.7f, 379.3f),
                new Line.Word("Names", 381.9f, 414.4f), new Line.Word("(URNs)\"", 417f, 453.9f),
                new Line.Word("RFC", 459f, 477.6f), new Line.Word("8141", 480.2f, 502.5f), new Line.Word("DOI", 507.6f, 526f)));

        assertThat(LateText.insert(List.of(before, punctuation, after, words))).extracting(Line::text).containsExactly(
                "[rfc2119]>.",
                "Saint-Andre, P. and J. Klensin, \"Uniform Resource Names (URNs)\", RFC 8141, DOI",
                "10.17487/RFC8141, April 2017");
    }

    @Test
    void linesOfTwoColumnsOnOneBaselineStayApart() {
        // two-column paper: both columns have a line on the same baseline, 24 pt apart
        Line left = at(100, line(10, "left column text", new Line.Word("left", 50, 70), new Line.Word("text", 260, 282)));
        Line right = at(100, line(10, "right column text", new Line.Word("right", 306, 330), new Line.Word("text", 520, 545)));

        assertThat(LateText.insert(List.of(left, right))).containsExactly(left, right);
    }

    @Test
    void lateLinesAboveTheTextOfThePageGoToItsTop() {
        // RFC 9562 page 6: list items 9 to 16 go on from page 5 and are drawn after the rest of the page
        Line text1 = at(217.5f, line(10, "An inspection of these", new Line.Word("An", 65.9f, 80f)));
        Line text2 = at(231.1f, line(10, "in which new UUIDs", new Line.Word("in", 65.9f, 75f)));
        Line item9 = at(81.2f, line(10, "9. [Sonyflake]", new Line.Word("9.", 75.2f, 82f)));
        Line item10 = at(97.3f, line(10, "10. [orderedUuid]", new Line.Word("10.", 69.6f, 82f)));

        assertThat(LateText.insert(List.of(text1, text2, item9, item10))).containsExactly(item9, item10, text1, text2);
    }

    @Test
    void rightColumnStartingAboveTheLeftOneStaysAfterIt() {
        // the left column starts below a figure, the right one at the top of the page
        Line left1 = at(300, line(10, "left one", new Line.Word("left", 50, 70)));
        Line left2 = at(314, line(10, "left two", new Line.Word("left", 50, 70)));
        Line right = at(80, line(10, "right one", new Line.Word("right", 308, 330)));

        assertThat(LateText.insert(List.of(left1, left2, right))).containsExactly(left1, left2, right);
    }

    private static Line at(float y, Line line) {
        return new Line(line.page(), line.pageHeight(), line.x(), y, line.fontSize(), false, false, line.text(), -1,
                line.width(), line.pageX(), y, line.words());
    }

    private static Line line(float size, String text, Line.Word... words) {
        float x = words[0].left();
        return new Line(1, 792, x, 281.1f, size, false, false, text, -1, words[words.length - 1].right() - x, x, 281.1f,
                List.of(words));
    }
}
