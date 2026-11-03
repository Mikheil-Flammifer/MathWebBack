package com.mathweb.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PaddleSignatureVerifierTest {

    private PaddleSignatureVerifier verifier;
    private final String testSecret = "test_webhook_secret_123";

    @BeforeEach
    void setUp() {
        verifier = new PaddleSignatureVerifier();
        ReflectionTestUtils.setField(verifier, "secret", testSecret);
    }

    @Test
    void testVerify_ValidSignature() throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"event\": \"test\"}";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(testSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ":").getBytes(StandardCharsets.UTF_8));
        mac.update(bodyBytes);
        String signature = HexFormat.of().formatHex(mac.doFinal());

        String header = "ts=" + ts + ";h1=" + signature;
        assertTrue(verifier.verify(header, bodyBytes));
    }

    @Test
    void testVerify_NullHeader() {
        assertFalse(verifier.verify(null, "test".getBytes()));
    }

    @Test
    void testVerify_NullBody() {
        assertFalse(verifier.verify("ts=123;h1=abc", null));
    }

    @Test
    void testVerify_BothNull() {
        assertFalse(verifier.verify(null, null));
    }

    @Test
    void testVerify_InvalidSignature() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        assertFalse(verifier.verify("ts=" + ts + ";h1=invalid", "test".getBytes()));
    }

    @Test
    void testVerify_WrongSecret() throws Exception {
        PaddleSignatureVerifier wrongVerifier = new PaddleSignatureVerifier();
        ReflectionTestUtils.setField(wrongVerifier, "secret", "wrong_secret");

        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"event\": \"test\"}";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(testSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ":").getBytes(StandardCharsets.UTF_8));
        mac.update(bodyBytes);
        String signature = HexFormat.of().formatHex(mac.doFinal());

        assertFalse(wrongVerifier.verify("ts=" + ts + ";h1=" + signature, bodyBytes));
    }

    @Test
    void testVerify_ExpiredTimestamp() throws Exception {
        long expiredTs = Instant.now().getEpochSecond() - 600;
        String ts = String.valueOf(expiredTs);
        String body = "{\"event\": \"test\"}";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(testSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ":").getBytes(StandardCharsets.UTF_8));
        mac.update(bodyBytes);
        String signature = HexFormat.of().formatHex(mac.doFinal());

        assertFalse(verifier.verify("ts=" + ts + ";h1=" + signature, bodyBytes));
    }

    @Test
    void testVerify_MissingTs() {
        assertFalse(verifier.verify("h1=abc123", "test".getBytes()));
    }

    @Test
    void testVerify_MissingH1() {
        assertFalse(verifier.verify("ts=123", "test".getBytes()));
    }

    @Test
    void testVerify_NullSecret() {
        PaddleSignatureVerifier nullVerifier = new PaddleSignatureVerifier();
        ReflectionTestUtils.setField(nullVerifier, "secret", null);
        assertFalse(nullVerifier.verify("ts=123;h1=abc", "test".getBytes()));
    }
}