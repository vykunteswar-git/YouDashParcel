package com.youdash.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final long expiration;
    private final Key key;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {
        this.expiration = expiration;
        // Generate a safe key from the secret
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /** Claim name for USER / ADMIN; avoid {@code "type"} — some JWT stacks serialize it as {@code typ}, breaking admin checks. */
    public static final String CLAIM_ACCOUNT_TYPE = "tokenType";

    public String generateToken(Long id, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put(CLAIM_ACCOUNT_TYPE, type);
        return createToken(claims, String.valueOf(id));
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public Long extractId(String token) {
        final Claims claims = extractAllClaims(token);
        Object raw = claims.get("id");
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Long.parseLong(s.trim());
        }
        // JSON integers often deserialize as Integer; Long.class lookup can return null.
        if (raw == null && claims.getSubject() != null && !claims.getSubject().isBlank()) {
            try {
                return Long.parseLong(claims.getSubject().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public String extractType(String token) {
        final Claims claims = extractAllClaims(token);
        String t = claims.get(CLAIM_ACCOUNT_TYPE, String.class);
        if (t == null) {
            t = claims.get("type", String.class);
        }
        if (t == null) {
            t = claims.get("typ", String.class);
        }
        return t;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
