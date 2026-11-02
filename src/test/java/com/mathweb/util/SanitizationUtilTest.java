package com.mathweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SanitizationUtilTest {

    // ===== cleanText tests =====

    @Test
    void testCleanText_NormalText() {
        String input = "Hello, World!";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("Hello, World!", result);
    }

    @Test
    void testCleanText_WithNullBytes() {
        String input = "Hello\0World";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("HelloWorld", result);
    }

    @Test
    void testCleanText_WithControlCharacters() {
        String input = "Hello\u0001\u0002World";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("HelloWorld", result);
    }

    @Test
    void testCleanText_PreservesNewlines() {
        String input = "Hello\nWorld";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("Hello\nWorld", result);
    }

    @Test
    void testCleanText_PreservesTabs() {
        String input = "Hello\tWorld";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("Hello\tWorld", result);
    }

    @Test
    void testCleanText_RemovesCarriageReturn() {
        String input = "Hello\rWorld";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("HelloWorld", result);
    }

    @Test
    void testCleanText_TrimsWhitespace() {
        String input = "  Hello, World!  ";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("Hello, World!", result);
    }

    @Test
    void testCleanText_NullInput() {
        String result = SanitizationUtil.cleanText(null);
        assertNull(result);
    }

    @Test
    void testCleanText_EmptyString() {
        String result = SanitizationUtil.cleanText("");
        assertEquals("", result);
    }

    @Test
    void testCleanText_WhitespaceOnly() {
        String input = "   \t\n   ";
        String result = SanitizationUtil.cleanText(input);
        assertEquals("", result);
    }

    // ===== sanitizeText tests (alias for cleanText) =====

    @Test
    void testSanitizeText() {
        String input = "Test\0input";
        String result = SanitizationUtil.sanitizeText(input);
        assertEquals("Testinput", result);
    }

    // ===== sanitizeMathText tests =====

    @Test
    void testSanitizeMathText() {
        String input = "x^2 + y^2\0";
        String result = SanitizationUtil.sanitizeMathText(input);
        assertEquals("x^2 + y^2", result);
    }

    // ===== sanitizeComment tests =====

    @Test
    void testSanitizeComment() {
        String input = "Great post!\t\n";
        String result = SanitizationUtil.sanitizeComment(input);
        assertEquals("Great post!", result);
    }

    // ===== stripHtml tests =====

    @Test
    void testStripHtml() {
        String input = "<script>alert('xss')</script>";
        String result = SanitizationUtil.stripHtml(input);
        // Note: stripHtml uses cleanText which doesn't remove HTML tags
        // It only removes control characters
        assertEquals("<script>alert('xss')</script>", result);
    }

    // ===== escapeHtml tests =====

    @Test
    void testEscapeHtml_NormalText() {
        String input = "Hello, World!";
        String result = SanitizationUtil.escapeHtml(input);
        assertEquals("Hello, World!", result);
    }

    @Test
    void testEscapeHtml_WithAmpersand() {
        String input = "Tom & Jerry";
        String result = SanitizationUtil.escapeHtml(input);
        assertEquals("Tom &amp; Jerry", result);
    }

    @Test
    void testEscapeHtml_WithLessThan() {
        String input = "5 < 10";
        String result = SanitizationUtil.escapeHtml(input);
        assertEquals("5 &lt; 10", result);
    }

    @Test
    void testEscapeHtml_WithGreaterThan() {
        String input = "10 > 5";
        String result = SanitizationUtil.escapeHtml(input);
        assertEquals("10 &gt; 5", result);
    }

    @Test
    void testEscapeHtml_WithDoubleQuotes() {
        String input = "Say \"Hello\"";
        String result = SanitizationUtil.escapeHtml(input);
        assertEquals("Say &quot;Hello&quot;", result);
    }

    @Test
    void testEscapeHtml_WithSingleQuotes() {
        String input = "It's working";
        String result = SanitizationUtil.escapeHtml(input);
        assertEquals("It&#x27;s working", result);
    }

    @Test
    void testEscapeHtml_WithAllSpecialChars() {
        String input = "<script>alert('XSS & more')</script>";
        String result = SanitizationUtil.escapeHtml(input);
        assertEquals("&lt;script&gt;alert(&#x27;XSS &amp; more&#x27;)&lt;/script&gt;", result);
    }

    @Test
    void testEscapeHtml_NullInput() {
        String result = SanitizationUtil.escapeHtml(null);
        assertNull(result);
    }

    @Test
    void testEscapeHtml_EmptyString() {
        String result = SanitizationUtil.escapeHtml("");
        assertEquals("", result);
    }

    // ===== isAllowedEmbedUrl tests =====

    @Test
    void testIsAllowedEmbedUrl_ValidDesmosUrl() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://www.desmos.com/calculator"));
    }

    @Test
    void testIsAllowedEmbedUrl_ValidDesmosUrlWithoutWww() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://desmos.com/calculator"));
    }

    @Test
    void testIsAllowedEmbedUrl_ValidGeogebraUrl() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://www.geogebra.org/graphing"));
    }

    @Test
    void testIsAllowedEmbedUrl_ValidGeogebraUrlWithoutWww() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://geogebra.org/graphing"));
    }

    @Test
    void testIsAllowedEmbedUrl_ValidSubdomain() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://calculator.desmos.com"));
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://app.geogebra.org"));
    }

    @Test
    void testIsAllowedEmbedUrl_HttpNotAllowed() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("http://desmos.com/calculator"));
    }

    @Test
    void testIsAllowedEmbedUrl_InvalidHost() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("https://example.com"));
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("https://evil.com"));
    }

    @Test
    void testIsAllowedEmbedUrl_WithCredentials() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("https://user:pass@desmos.com"));
    }

    @Test
    void testIsAllowedEmbedUrl_WithCustomPort() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("https://desmos.com:8080/calculator"));
    }

    @Test
    void testIsAllowedEmbedUrl_WithExplicitPort443() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://desmos.com:443/calculator"));
    }

    @Test
    void testIsAllowedEmbedUrl_NullInput() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl(null));
    }

    @Test
    void testIsAllowedEmbedUrl_EmptyString() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl(""));
    }

    @Test
    void testIsAllowedEmbedUrl_BlankString() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("   "));
    }

    @Test
    void testIsAllowedEmbedUrl_MalformedUrl() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("not-a-url"));
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("://desmos.com"));
    }

    @Test
    void testIsAllowedEmbedUrl_UrlTooLong() {
        StringBuilder longUrl = new StringBuilder("https://desmos.com/");
        for (int i = 0; i < 2000; i++) {
            longUrl.append("a");
        }
        assertFalse(SanitizationUtil.isAllowedEmbedUrl(longUrl.toString()));
    }

    @Test
    void testIsAllowedEmbedUrl_WithWhitespace() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("  https://desmos.com/calculator  "));
    }

    @Test
    void testIsAllowedEmbedUrl_MixedCaseScheme() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("HTTPS://desmos.com/calculator"));
    }

    @Test
    void testIsAllowedEmbedUrl_MixedCaseHost() {
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://DESMOS.COM/calculator"));
        assertTrue(SanitizationUtil.isAllowedEmbedUrl("https://GeOgEbRa.OrG/graphing"));
    }

    @Test
    void testIsAllowedEmbedUrl_FtpNotAllowed() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("ftp://desmos.com"));
    }

    @Test
    void testIsAllowedEmbedUrl_FileProtocolNotAllowed() {
        assertFalse(SanitizationUtil.isAllowedEmbedUrl("file:///path/to/file"));
    }
}
