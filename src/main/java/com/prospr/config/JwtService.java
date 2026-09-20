package com.prospr.config;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long hours;
    public JwtService(@Value("${prospr.jwt.secret}") String secret, @Value("${prospr.jwt.expiration-hours}") long hours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.hours = hours;
    }
    public String create(String userId, String email) {
        return Jwts.builder().subject(userId).claim("email", email).issuedAt(java.util.Date.from(Instant.now()))
            .expiration(java.util.Date.from(Instant.now().plus(hours, ChronoUnit.HOURS))).signWith(key).compact();
    }
    public String subject(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
}
