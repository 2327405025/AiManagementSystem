package com.example.taskmanager.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies HMAC JWTs. The secret stays in environment config, never in source.
 * 签发并校验 HMAC JWT。密钥只放在环境配置中，不写入源码。
 */
@Component
public class JwtService {
    private final byte[] secret;
    private final Duration ttl;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-ttl}") Duration ttl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        this.secret = bytes;
        this.ttl = ttl;
    }

    public String issue(AuthUser user) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.id().toString())
                .claim("username", user.username())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to sign JWT", exception);
        }
    }

    public AuthUser parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                throw new IllegalArgumentException("Expired JWT");
            }
            return new AuthUser(Long.parseLong(claims.getSubject()), claims.getStringClaim("username"));
        } catch (ParseException | JOSEException | NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid JWT", exception);
        }
    }
}
