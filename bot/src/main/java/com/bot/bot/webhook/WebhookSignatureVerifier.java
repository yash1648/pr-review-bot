package com.bot.bot.webhook;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bot.bot.config.GitHubProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSignatureVerifier {
    private final GitHubProperties gitHubProperties;

    public boolean verifySignature(String payload, String signature) {
        try {
            String computedSignature = computeSignature(payload);
            return computedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return false;
        }
    }

    private String computeSignature(String payload) throws Exception {
        String secret = gitHubProperties.getWebhookSecret();
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("GitHub webhook secret not configured");
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                0,
                secret.length(),
                "HmacSHA256"
        );
        mac.init(keySpec);

        byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + bytesToHex(result);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
