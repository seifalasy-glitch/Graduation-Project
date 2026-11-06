package com.edusync.edusync.backend.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import com.edusync.edusync.backend.users.entity.User;
import io.jsonwebtoken.JwtException;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 bytes"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails userDetails, User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        try {
            Claims claims = extractClaims(token);

            return claims.getSubject().equals(userDetails.getUsername())
                    && claims.getExpiration().after(new Date())
                    && userDetails.isEnabled();
        } catch (io.jsonwebtoken.JwtException |
                 IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenVersionValid(String token, User user) {

        try {
            Claims claims = extractClaims(token);
            Integer tokenVersion = claims.get(
                    "tokenVersion",
                    Integer.class
            );
            return tokenVersion != null
                    && tokenVersion.equals(user.getTokenVersion());

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}