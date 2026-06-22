# GitHub PR Review Bot — Usage Guide

## Quick Start (5 minutes)

### 1. Prerequisites

- Java 21+ with Maven 3.9+
- Ollama installed locally (`ollama pull qwen2.5-coder:7b && ollama serve`)
- A GitHub App created and installed on your repository

### 2. Create a GitHub App

Go to **GitHub Settings → Developer settings → GitHub Apps → New GitHub App**:

| Field | Value |
|-------|-------|
| App name | `pr-review-bot` |
| Homepage URL | `https://your-domain.com` |
| Webhook URL | `https://your-domain.com/webhook/github` |
| Webhook secret | Generate a random string (save it) |

**Repository permissions:**
- Pull Requests: Read & write
- Contents: Read
- Commit statuses: Read & write

**Subscribe to events:**
- Pull request
- Pull request review
- Installation

### 3. Generate a Private Key

1. In your GitHub App settings, scroll to **Private keys**
2. Click **Generate a private key**
3. Save the `.pem` file to `bot/certs/private-key.pem`
4. Copy the **App ID** from the app settings page

### 4. Install the App on Your Repository

1. In GitHub App settings, click **Install App** (left sidebar)
2. Select your repository
3. Click **Authorize**

### 5. Set Environment Variables

```bash
export GITHUB_APP_ID=123456
export GITHUB_CLIENT_ID=Iv1.xxxxx
export GITHUB_PRIVATE_KEY_PATH=/path/to/private-key.pem
export GITHUB_WEBHOOK_SECRET=your_webhook_secret
export LLM_MODEL=qwen2.5-coder:7b
export LLM_BASE_URL=http://localhost:11434
export LLM_TIMEOUT_SECONDS=60
export LLM_ENABLED=true
export HEURISTICS_ENABLED=true
export AUTO_APPROVE=false
export INLINE_COMMENTS=true
export REVIEW_SUMMARY_ENABLED=true
```

### 6. Build and Run

```bash
cd bot/
./mvnw clean package -DskipTests
java -jar bot/target/bot-0.0.1-SNAPSHOT.jar
```

The app starts on port 8080.

### 7. Verify the Webhook

1. Go to your GitHub App settings → **Advanced** → **Recent Deliveries**
2. Create a test PR on your repository
3. You should see a successful delivery (HTTP 200)

---

## How It Works

### Flow Diagram

```
User creates/updates a PR
    │
    ▼
GitHub sends webhook (POST /webhook/github)
    │
    ▼
GitHubWebhookController validates HMAC-SHA256 signature
    │
    ▼
ReviewOrchestrator.processPullRequest() (async)
    │
    ├── GitHubApiClient fetches PR context + diff
    ├── RepoConfigLoader fetches .prreview.yaml (per-repo config)
    │
    ▼
Dual analysis engines run in parallel:
    ├── HeuristicsAnalysisEngine (fast, regex-based rules)
    │     ├── SecretsDetectionRule
    │     └── NullPointerDetectionRule
    └── LLMReviewEngine (contextual, via OllamaClient)
    │
    ▼
FindingMerger.mergeAndRank() — dedup + sort by severity
    │
    ▼
ReviewPublisher.submitReview() — posts inline comments + summary
    │
    ▼
GitHub displays review on the PR
```

### What Happens When a PR Is Opened

1. **Webhook received** (< 1 second) — signature validated, 202 Accepted returned immediately
2. **Context fetched** (1-2 seconds) — PR metadata, diff, and per-repo `.prreview.yaml` loaded
3. **Heuristics analysis** (1-5 seconds) — regex-based rules scan for secrets, null pointer risks
4. **LLM analysis** (30-120 seconds) — Ollama reviews each changed chunk with contextual understanding
5. **Findings merged** (< 1 second) — duplicates removed, results ranked by severity
6. **Review published** (< 5 seconds) — inline comments on changed lines + structured summary

---

## Real-World Examples

### Example 1: Security Issue Detection

**Your code (added in PR):**
```java
String password = "admin123";
String query = "SELECT * FROM users WHERE id=" + userId;
```

**Bot response:**
```
🔴 CRITICAL — SECURITY: Hardcoded secret detected
  at src/main/java/com/example/UserService.java:42
  Suggestion: Remove secret and use environment variables or a vault

🔴 CRITICAL — SECURITY: Potential SQL injection
  at src/main/java/com/example/UserService.java:43
  Suggestion: Use parameterized queries:
    PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id=?");
    ps.setInt(1, userId);
```

### Example 2: Code Quality Warning

**Your code:**
```java
public void processData(List<Data> items) {
    if (items == null) {} // empty catch
    // 500+ lines...
}
```

**Bot response:**
```
🟠 HIGH — POTENTIAL_BUG: Empty catch block — exception silently ignored
  Suggestion: Add logging or handle the exception properly

🟡 MEDIUM — POTENTIAL_BUG: Chained method call without null-safe operator
  Suggestion: Use ?. for safe navigation or add explicit null checks
```

### Example 3: Clean Code

**Your code:**
```java
if (user != null && user.getProfile() != null) {
    return user.getProfile().getName();
}
```

**Bot response:**
```
✅ No issues found. The changes look good!
```

---

## Configuration Reference

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GITHUB_APP_ID` | Yes | — | GitHub App ID (numeric) |
| `GITHUB_CLIENT_ID` | Yes | — | GitHub App client ID |
| `GITHUB_WEBHOOK_SECRET` | Yes | — | Webhook secret for HMAC verification |
| `GITHUB_PRIVATE_KEY_PATH` | Yes | `certs/private-key.pem` | Path to the app's private key PEM file |
| `LLM_PROVIDER` | No | `ollama` | LLM provider: `ollama` or `nvidia-nim` |
| `LLM_MODEL` | No | `qwen2.5-coder:7b` | Model name (passed to provider) |
| `LLM_BASE_URL` | No | `http://localhost:11434` | Provider API base URL |
| `LLM_TIMEOUT_SECONDS` | No | `60` | LLM request timeout |
| `LLM_ENABLED` | No | `true` | Enable/disable LLM analysis |
| `LLM_API_KEY` | No | — | API key for NVIDIA NIM (`nvapi-...`) |
| `HEURISTICS_ENABLED` | No | `true` | Enable/disable heuristic analysis |
| `AUTO_APPROVE` | No | `false` | Auto-approve PRs with no issues |
| `INLINE_COMMENTS` | No | `true` | Post inline comments on diff lines |
| `REVIEW_SUMMARY_ENABLED` | No | `true` | Post summary comment on PR |

### Per-Repo Configuration (`.prreview.yaml`)

Place a `.prreview.yaml` file in the root of your repository to override global settings:

```yaml
# .prreview.yaml — per-repo configuration
enabled: true
auto_approve: true
inline_comments: false
review_summary: true
llm_model: qwen2.5-coder:7b
ignore_paths:
  - "*.md"
  - "package-lock.json"
  - "generated/*"
ignore_rules:
  - "NullPointerDetectionRule"
  - "DOCUMENTATION"
```

Supported fields:

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | boolean | Enable/disable reviews for this repo (default: true) |
| `auto_approve` | boolean | Override global auto-approve setting |
| `inline_comments` | boolean | Override global inline comments setting |
| `review_summary` | boolean | Post a summary comment on the PR |
| `llm_model` | string | Override the LLM model for this repo |
| `ignore_paths` | string[] | File path patterns to skip during analysis |
| `ignore_rules` | string[] | Rule names or categories to ignore |

---

## Customization

### Adding Custom Heuristic Rules

Create a new rule by implementing the `Rule` interface:

```java
package com.bot.bot.analysis;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class LoggingPracticeRule implements Rule {
    @Override
    public List<Finding> analyze(ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < chunk.getAddedLines().size(); i++) {
            String line = chunk.getAddedLines().get(i);
            if (line.contains("System.out.println")) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine() + i)
                        .severity("MEDIUM")
                        .category("CODE_STYLE")
                        .message("Use a logger instead of System.out.println")
                        .suggestion("Replace with: log.info(...)")
                        .source("HEURISTIC")
                        .confidence(0.95)
                        .precedenceScore(400)
                        .build());
            }
        }
        return findings;
    }

    @Override
    public String getName() { return "LoggingPracticeRule"; }

    @Override
    public int getPriority() { return 400; }
}
```

The `Rule` interface:
- `analyze(ChangeChunk)` — returns findings for a single file's change chunk
- `getName()` — unique rule name (used for `ignore_rules` in `.prreview.yaml`)
- `getPriority()` — execution order (higher runs first)

### Finding Structure

The `Finding` class is a POJO with these fields:

```java
Finding.builder()
    .id(UUID.randomUUID().toString())
    .filePath("src/main/java/com/example/Foo.java")
    .lineNumber(42)
    .endLine(45)               // 0 for single-line
    .severity("CRITICAL")      // CRITICAL, HIGH, MEDIUM, LOW, INFO
    .category("SECURITY")      // SECURITY, POTENTIAL_BUG, CODE_STYLE, etc.
    .message("Description of the issue")
    .suggestion("How to fix it")
    .source("HEURISTIC")       // HEURISTIC or LLM
    .confidence(0.95)          // 0.0 to 1.0
    .precedenceScore(1000)     // Higher = sorted first
    .build();
```

### Severity Levels

| Severity | When to Use | Default Threshold |
|----------|-------------|-------------------|
| `CRITICAL` | Security issues, hardcoded secrets | confidence >= 0.9 |
| `HIGH` | Resource leaks, SQL injection risks | confidence >= 0.8 |
| `MEDIUM` | Null pointer risks, empty catches | confidence >= 0.7 |
| `LOW` | Code style, minor improvements | confidence >= 0.5 |
| `INFO` | Best practice suggestions | any |

### Inline Comment Configuration

Inline comments are placed directly on the changed lines in the PR diff. Configure with:

```yaml
# Global (env var)
INLINE_COMMENTS=true

# Per-repo (.prreview.yaml)
inline_comments: true
```

When enabled, findings with a valid `filePath` and `lineNumber > 0` are posted as inline comments. Multi-line findings use `startLine` and `endLine` for range highlighting. Findings without precise line numbers appear only in the summary.

---

## API Reference

The bot exposes a minimal API surface:

### `POST /webhook/github`
Receives GitHub webhook events. Validates HMAC-SHA256 signature, processes `pull_request` events (opened, synchronize, reopened).

**Headers:**
- `X-GitHub-Event`: Event type (e.g., `pull_request`)
- `X-Hub-Signature-256`: HMAC-SHA256 signature
- `X-GitHub-Delivery`: Unique delivery ID

**Response:** `202 Accepted` (processing is async)

### `GET /actuator/health`
Spring Boot Actuator health endpoint.

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### `GET /webhook/health`
Simple liveness check.

```bash
curl http://localhost:8080/webhook/health
# OK
```

---

## Monitoring

### Health Checks

```
# Spring Boot Actuator
GET /actuator/health    → {"status":"UP"}

# Simple liveness
GET /webhook/health     → OK
```

### Logs

```bash
# Local
tail -f logs/app.log

# Docker
docker logs pr-review-bot --tail 100 -f

# Key log patterns
# "Starting PR review process"
# "Processing PR owner/repo/#123"
# "Final X findings after deduplication and ranking"
# "Review published successfully for owner/repo/#123"
```

Enable debug logging in `application.yaml`:

```yaml
logging:
  level:
    com.bot.bot: DEBUG
```

### Key Metrics to Track

- Webhooks received per hour
- Analysis success rate
- Average analysis time (heuristics vs LLM)
- Critical findings per PR
- False positive rate (track user feedback)

---

## Performance Tips

### Thread Pool Tuning

The analysis runs asynchronously on Spring's task executor. Tune in `application.yaml`:

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 10
        queue-capacity: 100
```

### Reduce Analysis Scope

Skip files by path in `.prreview.yaml`:

```yaml
ignore_paths:
  - "*.md"
  - "*.json"
  - "*.xml"
  - "generated/*"
  - "test/**"
```

### Confidence Thresholds

Filter low-confidence findings in custom rules:

```java
// Only report findings above confidence threshold
if (finding.getConfidence() < 0.7) {
    continue; // Skip uncertain findings
}
```

### LLM Timeout

If the LLM is slow, reduce the timeout:

```yaml
llm:
  timeout-seconds: 30
```

### Disable LLM for Speed

For faster (but less thorough) reviews, disable LLM analysis:

```bash
export LLM_ENABLED=false
```

---

## Production Deployment

### Build the JAR

```bash
cd bot/
./mvnw clean package -DskipTests
# Output: bot/target/bot-0.0.1-SNAPSHOT.jar
```

### Docker

```dockerfile
FROM eclipse-temurin:21-jdk-slim
WORKDIR /app
COPY target/bot-0.0.1-SNAPSHOT.jar app.jar
COPY certs/ /app/certs/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
cd bot/
docker build -t pr-review-bot:latest .
docker run -d \
  -p 8080:8080 \
  -e GITHUB_APP_ID=123456 \
  -e GITHUB_WEBHOOK_SECRET=your_secret \
  -e LLM_BASE_URL=http://host.docker.internal:11434 \
  -v $(pwd)/certs:/app/certs \
  --name pr-review-bot \
  pr-review-bot:latest
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pr-review-bot
spec:
  replicas: 2
  selector:
    matchLabels:
      app: pr-review-bot
  template:
    metadata:
      labels:
        app: pr-review-bot
    spec:
      containers:
      - name: bot
        image: your-registry/pr-review-bot:latest
        ports:
        - containerPort: 8080
        env:
        - name: GITHUB_APP_ID
          valueFrom:
            secretKeyRef:
              name: github-secrets
              key: app-id
        - name: GITHUB_WEBHOOK_SECRET
          valueFrom:
            secretKeyRef:
              name: github-secrets
              key: webhook-secret
        - name: GITHUB_PRIVATE_KEY_PATH
          value: /etc/secrets/private-key.pem
        volumeMounts:
        - name: github-key
          mountPath: /etc/secrets
      volumes:
      - name: github-key
        secret:
          secretName: github-private-key
```

The K8s deployment uses only the required env vars: `GITHUB_APP_ID`, `GITHUB_WEBHOOK_SECRET`, and `GITHUB_PRIVATE_KEY_PATH`. No `GITHUB_CLIENT_ID` is needed at runtime.

For full deployment details (Docker Compose, systemd, etc.), see [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Troubleshooting

### Webhook Not Triggering

1. Verify the webhook URL is publicly accessible:
   ```bash
   curl https://your-domain.com/webhook/github
   # Should return 400 (not 404)
   ```
2. Check `GITHUB_WEBHOOK_SECRET` matches the GitHub App settings
3. Confirm the app is installed on the repository (Repo Settings → GitHub Apps)

### Signature Validation Fails

```
Error: Invalid webhook signature
```

- Webhook secret must match exactly — check for extra spaces or newlines
- Verify the header name is `X-Hub-Signature-256`
- Ensure the app reads the secret from `GITHUB_WEBHOOK_SECRET`

### Analysis Hangs or Times Out

1. Check Ollama is running: `curl http://localhost:11434/api/tags`
2. Verify the model is pulled: `ollama pull qwen2.5-coder:7b`
3. Increase the LLM timeout: `LLM_TIMEOUT_SECONDS=120`
4. Check thread pool capacity in logs for task rejection

### Private Key Errors

```
Error: Failed to generate JWT token
```

- Verify `GITHUB_PRIVATE_KEY_PATH` points to a valid PEM file
- Both PKCS#1 (`-----BEGIN RSA PRIVATE KEY-----`) and PKCS#8 (`-----BEGIN PRIVATE KEY-----`) formats are supported
- Check file permissions are readable by the Java process

### Debug Checklist

- [ ] GitHub App created and installed on the repository
- [ ] Private key file exists and is readable
- [ ] All environment variables set (see config table above)
- [ ] App is running on port 8080
- [ ] Webhook URL is public (not localhost)
- [ ] Created a test PR to trigger analysis
- [ ] Checked GitHub App Recent Deliveries for HTTP 200
- [ ] Look for "Starting PR review process" in application logs
- [ ] Bot review appears on the pull request

---

## Component Reference

| Component | Package | Purpose |
|-----------|---------|---------|
| `GitHubWebhookController` | `webhook` | Receives and validates webhook events |
| `WebhookSignatureVerifier` | `webhook` | HMAC-SHA256 verification |
| `ReviewOrchestrator` | `service` | Orchestrates the full review pipeline |
| `GitHubApiClient` | `github` | Fetches PR data, diff, submits reviews |
| `GitHubJwtGenerator` | `github` | Generates JWT tokens for GitHub API auth |
| `HeuristicsAnalysisEngine` | `analysis` | Runs regex-based heuristic rules |
| `LLMReviewEngine` | `analysis` | Runs LLM-based contextual review |
| `OllamaClient` | `llm` | HTTP client for Ollama API |
| `FindingMerger` | `engine` | Deduplicates and ranks findings |
| `ReviewPublisher` | `engine` | Submits review to GitHub API |
| `UnifiedDiffParser` | `diff` | Parses unified diff into ChangeChunks |
| `RepoConfigLoader` | `config` | Loads `.prreview.yaml` per-repo config |
| `Rule` (interface) | `analysis` | Contract for heuristic analysis rules |

---

## Next Steps

1. **Monitor** — Watch PRs and review quality; check logs for errors
2. **Tune** — Adjust severity levels and confidence thresholds for your team
3. **Extend** — Add custom heuristic rules specific to your codebase
4. **Integrate** — Connect with Slack, JIRA, or other tools via webhooks
5. **Scale** — Deploy with Docker/Kubernetes (see [DEPLOYMENT.md](DEPLOYMENT.md))
