package com.bookstore.auth.service;

import com.bookstore.auth.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT Service for token generation, validation, and parsing.
 */
@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey secretKey;
    private final long jwtExpirationMs;

    public JwtService(
            @Value("${jwt.secret:change-this-in-production}") String jwtSecret,
            @Value("${jwt.expiration:3600000}") long jwtExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * Generate JWT token for user.
     */
    public String generateToken(User user, String correlationId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("status", user.getStatus().toString());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusSeconds(jwtExpirationMs / 1000);

        String token = Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(Date.from(expiry.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        logger.info("[{}] Generated JWT token for user: {} (expires: {})",
                   correlationId, user.getEmail(), expiry);

        return token;
    }

    /**
     * Generate refresh token.
     */
    public String generateRefreshToken(User user, String correlationId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("type", "refresh");

        // Refresh tokens last 30 days
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(30);

        String token = Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(Date.from(expiry.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        logger.info("[{}] Generated refresh token for user: {} (expires: {})",
                   correlationId, user.getEmail(), expiry);

        return token;
    }

    /**
     * Extract username from token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user ID from token.
     */
    public UUID extractUserId(String token) {
        String userIdStr = extractClaim(token, claims -> claims.get("userId", String.class));
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    /**
     * Extract expiration date from token.
     */
    public LocalDateTime extractExpiration(String token) {
        return extractClaim(token, claims ->
            claims.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    /**
     * Extract specific claim from token.
     */
    public <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Validate token against user details.
     */
    public boolean validateToken(String token, User user) {
        try {
            final String username = extractUsername(token);
            return (username.equals(user.getEmail()) && !isTokenExpired(token));
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate token format and signature only.
     */
    public boolean validateTokenFormat(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            logger.warn("Token expired: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            logger.warn("Malformed token: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported token: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            logger.warn("Illegal token argument: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create SHA-256 hash of token for storage.
     */
    public String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}