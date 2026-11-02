package com.mathweb.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private String testSecret = "testSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm1234567890";
    private long accessTokenExpiration = 3600000L; // 1 hour
    private long refreshTokenExpiration = 86400000L; // 24 hours

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", accessTokenExpiration);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiration", refreshTokenExpiration);
    }

    @Test
    void testGenerateAccessToken() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateAccessToken(userPrincipal);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGenerateRefreshToken() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateRefreshToken(userPrincipal);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGetUserIdFromToken() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(123L)
                .email("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateAccessToken(userPrincipal);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        assertEquals(123L, userId);
    }

    @Test
    void testGetEmailFromToken() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateAccessToken(userPrincipal);
        String email = jwtTokenProvider.getEmailFromToken(token);

        assertEquals("test@example.com", email);
    }

    @Test
    void testGetExpirationFromToken() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateAccessToken(userPrincipal);
        Date expiration = jwtTokenProvider.getExpirationFromToken(token);

        assertNotNull(expiration);
        // Expiration should be approximately now + accessTokenExpiration
        long now = System.currentTimeMillis();
        long expectedExpiration = now + accessTokenExpiration;
        assertTrue(Math.abs(expiration.getTime() - expectedExpiration) < 1000); // within 1 second
    }

    @Test
    void testValidateToken_ValidToken() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateAccessToken(userPrincipal);
        boolean isValid = jwtTokenProvider.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidToken() {
        String invalidToken = "invalid.token.here";
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        assertFalse(isValid);
    }

    @Test
    void testValidateToken_ExpiredToken() {
        // Create a token with very short expiration
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", 1L); // 1ms expiration
        
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateAccessToken(userPrincipal);
        
        // Wait a bit to ensure token is expired
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean isValid = jwtTokenProvider.validateToken(token);
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_EmptyToken() {
        boolean isValid = jwtTokenProvider.validateToken("");
        assertFalse(isValid);
    }

    @Test
    void testValidateToken_NullToken() {
        boolean isValid = jwtTokenProvider.validateToken(null);
        assertFalse(isValid);
    }

    @Test
    void testTokenContainsCorrectClaims() {
        UserPrincipal userPrincipal = UserPrincipal.builder()
                .id(456L)
                .email("user@test.com")
                .password("password")
                .authorities(Collections.emptyList())
                .emailVerified(true)
                .active(true)
                .build();

        String token = jwtTokenProvider.generateAccessToken(userPrincipal);
        
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(testSecret));
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("456", claims.getSubject());
        assertEquals("user@test.com", claims.get("email"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }
}
