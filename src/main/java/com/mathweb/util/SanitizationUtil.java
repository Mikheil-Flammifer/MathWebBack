package com.mathweb.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public final class SanitizationUtil {

    private static final Set<String> EMBED_HOSTS = Set.of("desmos.com", "geogebra.org");
    private static final int MAX_URL_LENGTH = 2048;

    private SanitizationUtil() {}

    /** Removes null bytes and control characters, keeps \n and \t, drops \r. Never touches < > & or \. */
    public static String cleanText(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\n' || c == '\t') {
                sb.append(c);
            } else if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    // Kept so existing callers compile. All rendered as text on the frontend.
    public static String sanitizeText(String input)     { return cleanText(input); }
    public static String sanitizeMathText(String input) { return cleanText(input); }
    public static String sanitizeComment(String input)  { return cleanText(input); }
    public static String stripHtml(String input)        { return cleanText(input); }

    /** Use ONLY when putting user text into an HTML context, e.g. an HTML email body. */
    public static String escapeHtml(String input) {
        if (input == null) return null;
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /** https only, host is desmos.com / geogebra.org or a subdomain, no credentials, default port. */
    public static boolean isAllowedEmbedUrl(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) return false;
        try {
            URI uri = new URI(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            if (uri.getUserInfo() != null) return false;
            if (uri.getPort() != -1 && uri.getPort() != 443) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String allowed : EMBED_HOSTS) {
                if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}