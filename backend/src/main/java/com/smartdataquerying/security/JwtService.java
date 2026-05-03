package com.smartdataquerying.security;

import com.smartdataquerying.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.jwt().ttlMinutes() * 60)))
                .signWith(key)
                .compact();
    }

    public String validateAndGetSubject(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}

