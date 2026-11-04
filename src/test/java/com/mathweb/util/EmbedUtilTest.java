package com.mathweb.util;

import com.mathweb.enums.EmbedType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmbedUtilTest {

    @Test
    void testParse_DesmosValid() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abc123");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.DESMOS, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_DesmosWithTrailingSlash() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abc123/");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.DESMOS, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_DesmosWithQueryParams() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abc123?embed");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.DESMOS, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_DesmosWithHash() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abc123#test");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.DESMOS, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_DesmosMinimumLength() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abcde");
        assertTrue(result.isPresent());
        assertEquals("abcde", result.get().ref());
    }

    @Test
    void testParse_DesmosMaximumLength() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abcdefghij1234567890");
        assertTrue(result.isPresent());
        assertEquals("abcdefghij1234567890", result.get().ref());
    }

    @Test
    void testParse_DesmosTooShort() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abc");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_DesmosTooLong() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.desmos.com/calculator/abcdefghij12345678901");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_DesmosMissingWww() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://desmos.com/calculator/abc123");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_DesmosHttp() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("http://www.desmos.com/calculator/abc123");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_GeoGebraValidM() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/m/abc123");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.GEOGEBRA, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_GeoGebraValidClassic() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/classic/abc123");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.GEOGEBRA, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_GeoGebraValidGraphing() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/graphing/abc123");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.GEOGEBRA, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_GeoGebraValidGeometry() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/geometry/abc123");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.GEOGEBRA, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_GeoGebraValidMaterial() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/material/iframe/id/abc123");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.GEOGEBRA, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testParse_GeoGebraWithoutWww() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://geogebra.org/m/abc123");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.GEOGEBRA, result.get().type());
    }

    @Test
    void testParse_GeoGebraMinimumLength() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/m/abc");
        assertTrue(result.isPresent());
        assertEquals("abc", result.get().ref());
    }

    @Test
    void testParse_GeoGebraMaximumLength() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/m/abcdefghij1234567890");
        assertTrue(result.isPresent());
        assertEquals("abcdefghij1234567890", result.get().ref());
    }

    @Test
    void testParse_GeoGebraTooShort() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/m/ab");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_GeoGebraTooLong() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://www.geogebra.org/m/abcdefghij12345678901");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_GeoGebraHttp() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("http://www.geogebra.org/m/abc123");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_NullInput() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse(null);
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_EmptyString() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_BlankString() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("   ");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_InvalidUrl() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("https://example.com/abc123");
        assertFalse(result.isPresent());
    }

    @Test
    void testParse_WithWhitespace() {
        Optional<EmbedUtil.Embed> result = EmbedUtil.parse("  https://www.desmos.com/calculator/abc123  ");
        assertTrue(result.isPresent());
        assertEquals(EmbedType.DESMOS, result.get().type());
        assertEquals("abc123", result.get().ref());
    }

    @Test
    void testIframeUrl_Desmos() {
        String url = EmbedUtil.iframeUrl(EmbedType.DESMOS, "abc123");
        assertEquals("https://www.desmos.com/calculator/abc123?embed", url);
    }

    @Test
    void testIframeUrl_GeoGebra() {
        String url = EmbedUtil.iframeUrl(EmbedType.GEOGEBRA, "abc123");
        assertEquals("https://www.geogebra.org/material/iframe/id/abc123", url);
    }

    @Test
    void testIframeUrl_NullType() {
        String url = EmbedUtil.iframeUrl(null, "abc123");
        assertNull(url);
    }

    @Test
    void testIframeUrl_NullRef() {
        String url = EmbedUtil.iframeUrl(EmbedType.DESMOS, null);
        assertNull(url);
    }

    @Test
    void testIframeUrl_BothNull() {
        String url = EmbedUtil.iframeUrl(null, null);
        assertNull(url);
    }

    @Test
    void testIframeUrl_EmptyRef() {
        String url = EmbedUtil.iframeUrl(EmbedType.DESMOS, "");
        assertEquals("https://www.desmos.com/calculator/?embed", url);
    }
}