package com.mathweb.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class PaddleSignatureVerifier {

    private static final long TOLERANCE_SECONDS = 300;

    @Value("${paddle.webhook-secret:}")
    private String secret;

    public boolean verify(String header, byte[] body) {
        if (secret == null || secret.isBlank() || header == null || body == null) return false;

        String ts = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            if (key.equals("ts")) ts = kv[1].trim();
            else if (key.equals("h1")) signatures.add(kv[1].trim().toLowerCase());
        }
        if (ts == null || signatures.isEmpty()) return false;

        try {
            long sent = Long.parseLong(ts);
            if (Math.abs(Instant.now().getEpochSecond() - sent) > TOLERANCE_SECONDS) return false;
        } catch (NumberFormatException e) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((ts + ":").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            byte[] expected = HexFormat.of().formatHex(mac.doFinal()).getBytes(StandardCharsets.UTF_8);

            for (String sig : signatures) {
                if (MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8))) return true;
            }
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC setup failed", e);
        }
    }
}