package com.bot.bot.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.bot.bot.service.ReviewOrchestrator;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class GitHubWebhookController {
    private final WebhookSignatureVerifier signatureVerifier;
    private final ReviewOrchestrator reviewOrchestrator;
    private final Gson gson = new Gson();

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId) {

        log.info("Received GitHub webhook event: {} (delivery: {})", eventType, deliveryId);

        // Verify webhook signature
        if (!signatureVerifier.verifySignature(payload, signature)) {
            log.warn("Invalid webhook signature for delivery: {}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        // Only process pull request events
        if (!"pull_request".equals(eventType)) {
            log.debug("Ignoring non-PR event: {}", eventType);
            return ResponseEntity.ok("Event ignored");
        }

        try {
            JsonObject webhookData = gson.fromJson(payload, JsonObject.class);
            String action = webhookData.get("action").getAsString();

            // Process PR opened, synchronize, or reopened
            if ("opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action)) {
                log.info("Processing PR action: {}", action);
                reviewOrchestrator.processPullRequest(webhookData);
                return ResponseEntity.accepted().body("Processing started");
            } else {
                log.debug("Ignoring PR action: {}", action);
                return ResponseEntity.ok("Action ignored");
            }
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}