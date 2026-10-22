package com.mathweb.util;

public class SanitizationUtil {

    private SanitizationUtil() {}

    // Strip all HTML tags
    public static String stripHtml(String input) {
        if (input == null) return null;
        return input.replaceAll("<[^>]*>", "").trim();
    }

    // Strip HTML but keep basic formatting
    public static String sanitizeComment(String input) {
        if (input == null) return null;
        // Remove script tags and event handlers
        String sanitized = input
                .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                .replaceAll("(?i)<[^>]*on\\w+\\s*=\\s*['\"][^'\"]*['\"][^>]*>", "")
                .replaceAll("(?i)javascript:", "")
                .replaceAll("(?i)vbscript:", "")
                .trim();
        return sanitized;
    }

    // Sanitize for safe display
    public static String escapeHtml(String input) {
        if (input == null) return null;
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    // Sanitize plain text fields
    public static String sanitizeText(String input) {
        if (input == null) return null;
        return stripHtml(input).trim();
    }

    private static final java.util.regex.Pattern DANGEROUS_TAGS = java.util.regex.Pattern.compile(
            "(?is)<!--.*?-->|<\\s*(script|style)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>"
                    + "|<\\s*/?\\s*(script|style|iframe|object|embed|link|meta|base|form)\\b[^>]*>");

    // For math content: keeps < and > (inequalities), removes only dangerous HTML tags
    public static String sanitizeMathText(String input) {
        if (input == null) return null;
        String result = input;
        String previous;
        do {                       // repeat so nested tricks like <scr<script>ipt> don't survive
            previous = result;
            result = DANGEROUS_TAGS.matcher(result).replaceAll("");
        } while (!result.equals(previous));
        return result.trim();
    }
}