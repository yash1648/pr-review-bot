# PR Review Bot

An automated code review system that integrates with GitHub to provide real-time feedback on pull requests using static heuristics and local LLM inference (Ollama).

## Features

- **Inline line-level comments**: Comments placed directly on the relevant line in the diff (like CodeRabbit)
- **Structured PR summaries**: Severity table with file-by-file breakdown
- **Dual analysis engine**: Fast static heuristics (secrets, null checks) + deep LLM analysis (contextual code review)
- **Per-repo configuration**: Each repository can customize behavior via `.prreview.yaml` in the repo root
- **Auto-approve**: Automatically approves PRs when no issues are found
- **Privacy-preserving**: Local LLM inference via Ollama -- no code leaves your network
- **HMAC-SHA256 verification**: All webhooks are cryptographically verified
- **Spring Boot Actuator**: Health checks and metrics at `/actuator/health`

## Architecture

The system processes pull requests through a pipeline:

1. **Webhook Layer**: Receives GitHub `pull_request` events, verifies HMAC-SHA256 signature
2. **GitHub Integration**: Authenticates via GitHub App JWT, fetches PR context and diff
3. **Diff Processing**: Parses unified diff into structured `ChangeChunk` objects (file path, line ranges, added/removed lines)
4. **Dual Analysis Engine**:
   - Static heuristics (regex-based, fast, deterministic) -- `SecretsDetectionRule`, `NullPointerDetectionRule`
   - LLM review (contextual, thorough, using Ollama) -- `LLMReviewEngine`
5. **Finding Merger**: Deduplicates and ranks findings by severity and confidence via `FindingMerger`
6. **Review Publisher**: Posts formatted inline comments and a structured summary to GitHub via `ReviewPublisher.submitReview()`

## Quick Start

### Prerequisites

- Java 21+ with Maven 3.9+
- Ollama installed locally
- GitHub App configured
- Git

### 1. Install Ollama and Pull a Model

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull qwen2.5-coder:7b
ollama serve          # runs on http://localhost:11434
```

### 2. Create a GitHub App

1. Go to GitHub Settings > Developer settings > GitHub Apps > New GitHub App
2. Fill in the form:
   - **App name**: `pr-review-bot`
   - **Homepage URL**: `https://github.com/yourusername/pr-review-bot`
   - **Webhook URL**: `https://your-domain.com/webhook/github` (use ngrok for local dev)
   - **Webhook secret**: Generate a secure random string
3. Set permissions:
   - Pull requests: **Read & Write**
   - Contents: **Read**
4. Subscribe to events: `pull_request`
5. Download the private key (`.pem` file)
6. Note your App ID, Client ID, and Webhook Secret

### 3. Configure the Application

```bash
git clone https://github.com/yourusername/pr-review-bot.git
cd pr-review-bot

# Create the .env file
cp .env.example .env

# Edit with your GitHub App credentials
nano .env
```

**Example .env**:

```
GITHUB_APP_ID=12345
GITHUB_CLIENT_ID=Iv1.abcdef123456
GITHUB_WEBHOOK_SECRET=your-webhook-secret
GITHUB_PRIVATE_KEY_PATH=certs/private-key.pem
LLM_MODEL=qwen2.5-coder:7b
LLM_BASE_URL=http://localhost:11434
```

The `.env` file is loaded automatically at startup -- no need to export variables manually. The loader checks `bot/.env` and `./.env`.

### 4. Set Up the Private Key

```bash
mkdir -p certs
cp ~/Downloads/your-app.pem certs/private-key.pem
```

### 5. Build and Run

```bash
# Build the JAR
./mvnw clean package -DskipTests

# Run
java -jar target/bot-0.0.1-SNAPSHOT.jar

# Application starts on http://localhost:8080
```

### 6. Expose Your Webhook (Local Development)

```bash
ngrok http 8080
# Forwarding https://abc123.ngrok.io -> http://localhost:8080
```

Update your GitHub App's Webhook URL to `https://abc123.ngrok.io/webhook/github`.

### 7. Test

1. Create a test repository and install the GitHub App
2. Open a pull request with code changes
3. The bot analyzes the PR and posts inline comments + summary within seconds

## Configuration

All configuration is handled via environment variables (or `.env` file):

| Variable                  | Description                       | Default                      |
|---------------------------|-----------------------------------|------------------------------|
| `GITHUB_APP_ID`           | GitHub App ID                     | Required                     |
| `GITHUB_CLIENT_ID`        | GitHub App Client ID              | Required                     |
| `GITHUB_WEBHOOK_SECRET`   | Webhook signature secret          | Required                     |
| `GITHUB_PRIVATE_KEY_PATH` | Path to private key `.pem` file   | `certs/private-key.pem`      |
| `LLM_MODEL`               | Ollama model name                 | `qwen2.5-coder:7b`           |
| `LLM_BASE_URL`            | Ollama API base URL               | `http://localhost:11434`     |
| `LLM_TIMEOUT_SECONDS`     | LLM request timeout               | `60`                         |
| `LLM_ENABLED`             | Enable/disable LLM analysis       | `true`                       |
| `HEURISTICS_ENABLED`      | Enable/disable heuristic rules    | `true`                       |
| `AUTO_APPROVE`            | Auto-approve PR when no findings  | `false`                      |
| `INLINE_COMMENTS`         | Post inline line-level comments   | `true`                       |
| `REVIEW_SUMMARY_ENABLED`  | Post structured summary comment   | `true`                       |

### Per-Repository Configuration (`.prreview.yaml`)

Each repository can override global settings by placing a `.prreview.yaml` file in the root of the default branch. This file is fetched from GitHub at review time.

```yaml
# .prreview.yaml
ignore_paths:
  - "*.md"
  - "package-lock.json"
  - "generated/*"
ignore_rules:
  - "NullPointerDetection"
llm_model: "llama3.2:latest"        # overrides LLM_MODEL for this repo
auto_approve: true                   # overrides AUTO_APPROVE
inline_comments: false               # overrides INLINE_COMMENTS
```

## Project Structure

```
pr-review-bot/
├── src/main/java/com/bot/bot/
│   ├── config/
│   │   ├── AppProperties.java       # Core app settings
│   │   ├── GitHubProperties.java    # GitHub integration settings
│   │   ├── LLMProperties.java       # LLM/Ollama settings
│   │   ├── ReviewConfig.java        # .prreview.yaml model
│   │   ├── RepoConfigLoader.java    # Fetches .prreview.yaml from GitHub
│   │   ├── GsonConfig.java          # JSON serialization
│   │   └── WebClientConfig.java     # HTTP client config
│   ├── webhook/
│   │   ├── GitHubWebhookController.java   # POST /webhook/github
│   │   └── WebhookSignatureVerifier.java  # HMAC-SHA256 verification
│   ├── github/
│   │   ├── GitHubApiClient.java     # submitReview() with inline comments
│   │   └── GitHubJwtGenerator.java  # PKCS#1 and PKCS#8 compatible JWT
│   ├── domain/
│   │   ├── PullRequestContext.java  # PR metadata context
│   │   ├── ChangeChunk.java         # Parsed diff chunk
│   │   ├── Finding.java             # Review finding (with endLine)
│   │   └── ReviewComment.java       # Inline comment model
│   ├── diff/
│   │   └── UnifiedDiffParser.java   # Unified diff parser
│   ├── analysis/
│   │   ├── Rule.java                # Heuristic rule interface
│   │   ├── HeuristicsAnalysisEngine.java
│   │   ├── LLMReviewEngine.java
│   │   └── heuristics/
│   │       ├── SecretsDetectionRule.java
│   │       └── NullPointerDetectionRule.java
│   ├── llm/
│   │   └── OllamaClient.java        # Ollama API client
│   ├── engine/
│   │   ├── FindingMerger.java       # Dedup + rank findings
│   │   └── ReviewPublisher.java     # Posts inline comments + summary
│   ├── service/
│   │   └── ReviewOrchestrator.java  # Orchestrates the full pipeline
│   └── BotApplication.java          # Entry point with .env loader
├── src/main/resources/
│   └── application.yaml             # Spring Boot config
├── pom.xml                          # Maven (Spring Boot 4.0.2, Java 21)
├── .env.example                     # Example environment file
└── README.md
```

## Adding Custom Heuristic Rules

Implement the `Rule` interface and register it as a Spring `@Component`:

```java
@Component
public class MyCustomRule implements Rule {
    @Override
    public List<Finding> analyze(ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();

        for (String line : chunk.getAddedLines()) {
            if (/* your condition */) {
                findings.add(Finding.builder()
                    .id(UUID.randomUUID().toString())
                    .filePath(chunk.getFilePath())
                    .lineNumber(chunk.getStartLine())
                    .severity("HIGH")
                    .category("YOUR_CATEGORY")
                    .message("Your finding message")
                    .suggestion("How to fix it")
                    .source("HEURISTIC")
                    .confidence(0.85)
                    .precedenceScore(500)
                    .build());
            }
        }

        return findings;
    }

    @Override
    public String getName() {
        return "MyCustomRule";
    }

    @Override
    public int getPriority() {
        return 500;
    }
}
```

## API Endpoints

Base URL: `http://localhost:8080`

### `GET /webhook/health`

Simple liveness check.

```bash
curl http://localhost:8080/webhook/health
```

Response: `200 OK` — body: `OK`

### `GET /actuator/health`

Spring Boot Actuator health endpoint.

```bash
curl http://localhost:8080/actuator/health
```

```json
{"status": "UP"}
```

### `GET /actuator/info`

Application metadata.

```bash
curl http://localhost:8080/actuator/info
```

### `POST /webhook/github`

Inbound GitHub webhook endpoint for pull request events.

**Headers**

| Header                 | Description                                       |
|------------------------|---------------------------------------------------|
| `X-Hub-Signature-256`  | HMAC-SHA256 signature: `sha256=<hex-digest>`     |
| `X-GitHub-Event`       | Event type (expects `pull_request`)               |
| `X-GitHub-Delivery`    | Unique event ID (for logging and traceability)    |
| `Content-Type`         | `application/json`                                |

**Authentication**

The endpoint verifies the `X-Hub-Signature-256` header against the raw request body using `GITHUB_WEBHOOK_SECRET`. Invalid signatures are rejected with `401 Unauthorized`.

**Supported Events and Actions**

- Event: `pull_request`
- Actions: `opened`, `synchronize`, `reopened`

All other events/actions are acknowledged but ignored.

**Responses**

| Status | Body                    | Condition                                         |
|--------|-------------------------|---------------------------------------------------|
| 202    | `Processing started`    | Valid signature, supported event + action         |
| 200    | `Event ignored`         | Valid signature, `X-GitHub-Event` is not `pull_request` |
| 200    | `Action ignored`        | Valid signature, unsupported pull_request action  |
| 401    | `Invalid signature`     | Signature verification failed                     |
| 500    | `Error processing webhook` | Unexpected exception during dispatch           |

**Example Request**

```bash
PAYLOAD='{"action":"opened","pull_request":{"number":1,"title":"Test PR","body":"Test"},"repository":{"name":"repo","owner":{"login":"owner"}}}'
SIGNATURE=$(echo -n "$PAYLOAD" | \
  openssl dgst -sha256 -hmac "$GITHUB_WEBHOOK_SECRET" | \
  sed 's/^.* /sha256=/')

curl -v -X POST http://localhost:8080/webhook/github \
  -H "X-Hub-Signature-256: $SIGNATURE" \
  -H "X-GitHub-Event: pull_request" \
  -H "X-GitHub-Delivery: test-delivery-1" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"
```

Expected response: `202 Accepted` — body: `Processing started`

## Review Output Format

### Inline Comments

Findings are posted as line-level comments on the exact lines in the diff:

> **HIGH** — Potential null pointer dereference
>
> `user.getName()` could throw NPE if `user` is null. Add a null check before accessing `getName()`.
>
> *Suggested fix:* `if (user != null) { ... }`

### Structured Summary

A summary comment is posted on the PR with a severity table and file breakdown:

```
## PR Review Summary

### Findings by Severity

| Severity | Count |
|----------|-------|
| HIGH     | 2     |
| MEDIUM   | 1     |
| LOW      | 3     |

### Files Affected

| File | Issues |
|------|--------|
| src/main.ts | 2 |
| src/utils.ts | 1 |
| tests/test.ts | 3 |

### Recommendations

1.  **null-pointer** in `src/main.ts:42` — Add null check before accessing `user.name`
2.  **secrets** in `src/utils.ts:15` — Remove hardcoded API key
```

## Testing

```bash
cd pr-review-bot
./mvnw test
```

The test suite covers:

- Spring Boot context loading
- Webhook signature verification
- Diff parsing
- Heuristic rules (secrets detection, null pointer detection)
- LLM review integration
- Finding merging and deduplication
- Full review orchestration pipeline

## Monitoring

The application logs to console with timestamps:

```
2024-01-15 10:23:45 - Starting PR review process
2024-01-15 10:23:46 - Processing PR owner/repo/#123
2024-01-15 10:23:47 - Parsed 5 change chunks
2024-01-15 10:23:48 - Running heuristics analysis
2024-01-15 10:23:49 - Heuristics found 2 findings
2024-01-15 10:23:50 - Running LLM analysis
```

Configure logging in `application.yaml`:

```yaml
logging:
  level:
    com.bot.bot: DEBUG
```

## Troubleshooting

### LLM Not Available

**Error**: "LLM service unavailable"

**Solution**: Ensure Ollama is running:

```bash
ollama serve
```

### Invalid Webhook Signature

**Error**: "Invalid webhook signature"

**Solution**: Verify `GITHUB_WEBHOOK_SECRET` matches the value in your GitHub App settings.

### GitHub App Authentication Failed

**Error**: "Failed to generate JWT token"

**Solution**:

1. Verify the private key file exists at `GITHUB_PRIVATE_KEY_PATH` (default: `certs/private-key.pem`)
2. Ensure the key is in PEM format (both PKCS#1 and PKCS#8 are supported)
3. Check that `GITHUB_APP_ID` is correct

### Port Already in Use

```bash
# Use a different port
java -jar target/bot-0.0.1-SNAPSHOT.jar --server.port=9090

# Or kill the process on port 8080
lsof -i :8080
kill -9 <PID>
```

## Performance

- **Heuristics**: ~100ms for small files (parallel analysis across CPU cores)
- **LLM analysis**: ~5-30s depending on model size and code volume
- **End-to-end**: ~6-35s per PR (async webhook processing)

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

MIT License — see the LICENSE file for details.

## Support

For issues, questions, or suggestions:

1. Check existing GitHub issues
2. Create a detailed issue including logs
3. Include your Java, Maven, and Ollama versions
