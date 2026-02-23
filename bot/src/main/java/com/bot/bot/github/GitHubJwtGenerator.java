package com.bot.bot.github;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.bot.bot.config.GitHubProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubJwtGenerator {
    private final GitHubProperties gitHubProperties;
    private PrivateKey privateKey;

    public String generateAppToken() {
        try {
            PrivateKey key = getPrivateKey();
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(600); // 10 minutes

            return Jwts.builder()
                    .setIssuedAt(Date.from(now))
                    .setExpiration(Date.from(expiration))
                    .claim("iss", gitHubProperties.getAppId())
                    .signWith(key, SignatureAlgorithm.RS256)
                    .compact();
        } catch (Exception e) {
            log.error("Error generating JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    private PrivateKey getPrivateKey() throws Exception {
        if (privateKey == null) {
            privateKey = loadPrivateKey(gitHubProperties.getPrivateKeyPath());
        }
        return privateKey;
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String keyContent = new String(Files.readAllBytes(Paths.get(path)));

        // Remove PEM headers and newlines
        keyContent = keyContent
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decodedKey = java.util.Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }
}
