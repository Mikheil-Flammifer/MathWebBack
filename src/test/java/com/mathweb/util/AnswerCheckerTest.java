package com.mathweb.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnswerChecker}.
 * Covers the answer spec format ("2 || -2"), OPEN_ANSWER text matching,
 * number parsing (decimals, fractions, mixed numbers) and NUMERIC checking with tolerance.
 */
class AnswerCheckerTest {

    /** Parses a number or fails the test with a clear message. */
    private static BigDecimal parse(String raw) {
        return AnswerChecker.parseNumber(raw)
                .orElseThrow(() -> new AssertionError("expected a number for: \"" + raw + "\""));
    }

    // ==================== ACCEPTED ANSWERS ====================

    @Nested
    class AcceptedAnswers {

        @Test
        void nullSpec_returnsEmptyList() {
            assertThat(AnswerChecker.acceptedAnswers(null)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "||", " || ", "|| ||"})
        void blankSpec_returnsEmptyList(String spec) {
            assertThat(AnswerChecker.acceptedAnswers(spec)).isEmpty();
        }

        @Test
        void singleAnswer_returnsOneItem() {
            assertThat(AnswerChecker.acceptedAnswers("2")).containsExactly("2");
        }

        @Test
        void splitsOnDoublePipe() {
            assertThat(AnswerChecker.acceptedAnswers("2 || -2")).containsExactly("2", "-2");
            assertThat(AnswerChecker.acceptedAnswers("2||-2||3")).containsExactly("2", "-2", "3");
        }

        @Test
        void trimsEachAnswer() {
            assertThat(AnswerChecker.acceptedAnswers("  a  ||  b  ")).containsExactly("a", "b");
        }

        @Test
        void dropsEmptyEntries() {
            assertThat(AnswerChecker.acceptedAnswers("a || || b")).containsExactly("a", "b");
            assertThat(AnswerChecker.acceptedAnswers("||a")).containsExactly("a");
            assertThat(AnswerChecker.acceptedAnswers("a||")).containsExactly("a");
        }

        @Test
        void singlePipe_isNotASeparator() {
            assertThat(AnswerChecker.acceptedAnswers("a | b")).containsExactly("a | b");
        }
    }

    // ==================== TEXT (OPEN_ANSWER) ====================

    @Nested
    class CheckText {

        @ParameterizedTest(name = "spec=[{0}] given=[{1}] -> {2}")
        @CsvSource(delimiter = ';', value = {
                "x=2; x=2; true",
                "Pi; PI; true",
                "pi; Pi; true",
                "x = 2; x=2; true",
                "x=2; X = 2; true",
                "42; 42.; true",
                "42.; 42; true",
                "42; 42...; true",
                "x=2; x=3; false",
                "x=2; y=2; false",
                "abc; abcd; false",
        })
        void matchesIgnoringCaseSpacesAndTrailingDots(String spec, String given, boolean expected) {
            assertThat(AnswerChecker.checkText(spec, given)).isEqualTo(expected);
        }

        @Test
        void acceptsAnyOfSeveralAnswers() {
            assertThat(AnswerChecker.checkText("2 || -2", "2")).isTrue();
            assertThat(AnswerChecker.checkText("2 || -2", "-2")).isTrue();
            assertThat(AnswerChecker.checkText("2 || -2", "3")).isFalse();
            assertThat(AnswerChecker.checkText("x=2 || x=-2", "x = -2")).isTrue();
        }

        @Test
        void unicodeMinusAndEnDash_areTreatedAsMinus() {
            assertThat(AnswerChecker.checkText("-5", "−5")).isTrue();   // U+2212 minus sign
            assertThat(AnswerChecker.checkText("-5", "–5")).isTrue();   // U+2013 en dash
            assertThat(AnswerChecker.checkText("−5", "-5")).isTrue();
        }

        @Test
        void textType_doesNotTreatNumbersAsEquivalent() {
            // OPEN_ANSWER compares text; numeric equivalence is only for NUMERIC problems
            assertThat(AnswerChecker.checkText("0.5", "1/2")).isFalse();
            assertThat(AnswerChecker.checkText("1/2", "0.5")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n", ".", "..."})
        void blankGivenAnswer_isNeverCorrect(String given) {
            assertThat(AnswerChecker.checkText("5", given)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "||"})
        void missingSpec_isNeverCorrect(String spec) {
            assertThat(AnswerChecker.checkText(spec, "5")).isFalse();
        }

        @Test
        void normalizeText_handlesNullAndMessyInput() {
            assertThat(AnswerChecker.normalizeText(null)).isEmpty();
            assertThat(AnswerChecker.normalizeText("  A b.  ")).isEqualTo("ab");
            assertThat(AnswerChecker.normalizeText("− 5")).isEqualTo("-5");
        }
    }

    // ==================== PARSE NUMBER ====================

    @Nested
    class ParseNumber {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = ';', value = {
                // integers and decimals
                "3; 3",
                "0; 0",
                "-2.5; -2.5",
                "+2.5; 2.5",
                ".5; 0.5",
                "5.; 5",
                "123456789012345678901234567890; 123456789012345678901234567890",
                // decimal comma
                "0,5; 0.5",
                "-0,25; -0.25",
                // fractions
                "1/2; 0.5",
                "-3/4; -0.75",
                "6/3; 2",
                "1 / 2; 0.5",
                // mixed numbers
                "1 1/2; 1.5",
                "-1 1/2; -1.5",
                "+1 1/2; 1.5",
                "1   1/2; 1.5",
                "10 3/4; 10.75",
        })
        void parsesSupportedFormats(String raw, String expected) {
            assertThat(parse(raw)).isEqualByComparingTo(expected);
        }

        @Test
        void unicodeMinus_isUnderstood() {
            assertThat(parse("−5")).isEqualByComparingTo("-5");
            assertThat(parse("−3/4")).isEqualByComparingTo("-0.75");
        }

        @Test
        void surroundingWhitespace_isIgnored() {
            assertThat(parse("  7  ")).isEqualByComparingTo("7");
        }

        @Test
        void repeatingFractions_areAccurateToManyDigits() {
            BigDecimal diff = parse("1/3").subtract(new BigDecimal("0.3333333333")).abs();
            assertThat(diff).isLessThan(new BigDecimal("1E-9"));

            BigDecimal mixedDiff = parse("1 2/3").subtract(new BigDecimal("1.6666666667")).abs();
            assertThat(mixedDiff).isLessThan(new BigDecimal("1E-9"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                " ", "abc", "x", ".", "-", "+", "--5", "5-", "1.2.3",
                "1/", "/2", "1/2/3", "5%",
                "1/0", "-1/0", "1 1/0"          // zero denominator
        })
        void invalidInput_returnsEmpty(String raw) {
            assertThat(AnswerChecker.parseNumber(raw)).isEmpty();
        }
    }

    // ==================== NUMERIC ====================

    @Nested
    class CheckNumeric {

        @ParameterizedTest(name = "spec=[{0}] given=[{1}] -> {2}")
        @CsvSource(delimiter = ';', value = {
                // exact
                "5; 5; true",
                "5; 6; false",
                "2; 2.0; true",
                "2; 2.0000; true",
                // same value written differently
                "0.5; 1/2; true",
                "1/2; 0.5; true",
                "0.5; 0,5; true",
                "1.5; 1 1/2; true",
                "-0.75; -3/4; true",
                // default tolerance is 1e-9
                "1/3; 0.3333333333; true",
                "1/3; 0.333333; false",
                "1/3; 0.33; false",
                "1; 1.0000000001; true",
                "1; 1.0000001; false",
        })
        void defaultTolerance(String spec, String given, boolean expected) {
            assertThat(AnswerChecker.checkNumeric(spec, given, null)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "spec=[{0}] given=[{1}] tolerance={2} -> {3}")
        @CsvSource(delimiter = ';', value = {
                "3.14; 3.1416; 0.01; true",
                "3.14; 3.2; 0.01; false",
                "1/3; 0.33; 0.01; true",
                "100; 99; 1.5; true",
                "100; 98; 1.5; false",
                // exactly on the boundary is accepted
                "1; 1.5; 0.5; true",
                "1; 1.51; 0.5; false",
        })
        void customTolerance(String spec, String given, Double tolerance, boolean expected) {
            assertThat(AnswerChecker.checkNumeric(spec, given, tolerance)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "given=[{1}] tolerance={2} -> {3}")
        @CsvSource(delimiter = ';', value = {
                // zero or negative tolerance falls back to the default 1e-9
                "1; 1.0000001; 0; false",
                "1; 1.0000001; -1; false",
                "1; 1.0000000001; 0; true",
                "1; 1.0000000001; -0.5; true",
        })
        void zeroOrNegativeTolerance_usesDefault(String spec, String given, Double tolerance, boolean expected) {
            assertThat(AnswerChecker.checkNumeric(spec, given, tolerance)).isEqualTo(expected);
        }

        @Test
        void acceptsAnyOfSeveralAnswers() {
            assertThat(AnswerChecker.checkNumeric("2 || -2", "2", null)).isTrue();
            assertThat(AnswerChecker.checkNumeric("2 || -2", "-2", null)).isTrue();
            assertThat(AnswerChecker.checkNumeric("2 || -2", "3", null)).isFalse();
            assertThat(AnswerChecker.checkNumeric("1/2 || 0.75", "3/4", null)).isTrue();
            assertThat(AnswerChecker.checkNumeric("1/2 || 0.75", "0.6", null)).isFalse();
        }

        @Test
        void toleranceAppliesToEveryAcceptedAnswer() {
            assertThat(AnswerChecker.checkNumeric("10 || 20", "19.9", 0.5)).isTrue();
            assertThat(AnswerChecker.checkNumeric("10 || 20", "15", 0.5)).isFalse();
        }

        @Test
        void unparsableAcceptedAnswers_areIgnored() {
            assertThat(AnswerChecker.checkNumeric("abc || 5", "5", null)).isTrue();
            assertThat(AnswerChecker.checkNumeric("abc", "5", null)).isFalse();
            assertThat(AnswerChecker.checkNumeric("1/0 || 5", "5", null)).isTrue();
        }

        @Test
        void surroundingWhitespaceInGivenAnswer_isIgnored() {
            assertThat(AnswerChecker.checkNumeric("5", "  5  ", null)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "abc", "1/0", "1 1/0", "1.2.3", "--5"})
        void unparsableGivenAnswer_isNeverCorrect(String given) {
            assertThat(AnswerChecker.checkNumeric("5", given, null)).isFalse();
            assertThat(AnswerChecker.checkNumeric("5", given, 100.0)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "||", "abc"})
        void missingOrUnparsableSpec_isNeverCorrect(String spec) {
            assertThat(AnswerChecker.checkNumeric(spec, "5", null)).isFalse();
        }
    }
}