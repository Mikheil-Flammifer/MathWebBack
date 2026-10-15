package com.mathweb.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure answer-checking logic (no Spring), easy to unit test. */
public final class AnswerChecker {

    private AnswerChecker() {}

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("1E-9");
    private static final MathContext MC = new MathContext(30, RoundingMode.HALF_UP);

    private static final Pattern MIXED = Pattern.compile("^([+-]?)(\\d+) (\\d+)/(\\d+)$");
    private static final Pattern FRACTION = Pattern.compile("^([+-]?\\d+)/(\\d+)$");
    private static final Pattern DECIMAL = Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)$");

    /** "2 || -2" -> ["2", "-2"] */
    public static List<String> acceptedAnswers(String spec) {
        if (spec == null) return List.of();
        return Arrays.stream(spec.split("\\|\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ===== TEXT =====

    public static boolean checkText(String spec, String given) {
        String g = normalizeText(given);
        if (g.isEmpty()) return false;
        return acceptedAnswers(spec).stream().anyMatch(a -> normalizeText(a).equals(g));
    }

    static String normalizeText(String s) {
        if (s == null) return "";
        String t = s.replace('\u2212', '-').replace('\u2013', '-')
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        while (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t;
    }

    // ===== NUMERIC =====

    /** Understands 3, -2.5, 0,5, 1/2, -3/4 and mixed numbers like "1 1/2". */
    public static Optional<BigDecimal> parseNumber(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim()
                .replace('\u2212', '-').replace('\u2013', '-')
                .replace(',', '.')
                .replaceAll("\\s+", " ");

        Matcher m = MIXED.matcher(s);
        if (m.matches()) {
            BigDecimal den = new BigDecimal(m.group(4));
            if (den.signum() == 0) return Optional.empty();
            BigDecimal v = new BigDecimal(m.group(2))
                    .add(new BigDecimal(m.group(3)).divide(den, MC));
            return Optional.of("-".equals(m.group(1)) ? v.negate() : v);
        }

        s = s.replace(" ", "");
        m = FRACTION.matcher(s);
        if (m.matches()) {
            BigDecimal den = new BigDecimal(m.group(2));
            if (den.signum() == 0) return Optional.empty();
            return Optional.of(new BigDecimal(m.group(1)).divide(den, MC));
        }

        if (DECIMAL.matcher(s).matches()) {
            return Optional.of(new BigDecimal(s));
        }
        return Optional.empty();
    }

    public static boolean checkNumeric(String spec, String given, Double tolerance) {
        Optional<BigDecimal> g = parseNumber(given);
        if (g.isEmpty()) return false;

        BigDecimal tol = (tolerance == null || tolerance <= 0)
                ? DEFAULT_TOLERANCE
                : BigDecimal.valueOf(tolerance);

        for (String accepted : acceptedAnswers(spec)) {
            Optional<BigDecimal> v = parseNumber(accepted);
            if (v.isPresent() && v.get().subtract(g.get()).abs().compareTo(tol) <= 0) {
                return true;
            }
        }
        return false;
    }
}