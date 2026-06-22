# How to Use the PR Review Bot

A Spring Boot service that automatically reviews GitHub pull requests using heuristic analysis (secrets detection, null-pointer checks) and optional LLM review via Ollama. Posts inline line-level comments and a structured summary on every PR.

---

## TL;DR

```bash
# 1. Create a GitHub App at https://github.com/settings/apps
# 2. Create .env with your credentials
# 3. Build (from bot/ directory)
./mvnw clean package -DskipTests
# 4. Run
java -jar bot/target/bot-0.0.1-SNAPSHOT.jar
# 5. Open a PR — bot reviews it automatically
```

---

## What the Bot Does

- **Dual analysis engine**: heuristic rules (secrets, null pointers) + optional LLM review via Ollama
- **Inline line-level comments**: findings are posted on the exact diff lines, like CodeRabbit
- **Structured PR summary**: a single review comment with severity table, file breakdown, and suggestions
- **Per-repo configuration**: repos can include a `.prreview.yaml` file to customize rules per-project
- **Auto-approve**: when no issues are found, the bot can auto-approve the PR
- **HMAC-SHA256 verification**: every webhook payload is verified before processing

---

## Prerequisites

- Java 21+
- A GitHub App installed on your repository
- (Optional) Ollama running locally or remotely for LLM-based review

---

## Setup

### 1. Create a GitHub App

Go to **GitHub Settings > Developer settings > GitHub Apps > New GitHub App**.

| Field | Value |
|-------|-------|
| App name | `pr-review-bot` (or any unique name) |
| Webhook URL | `https://your-domain.com/webhook/github` |
| Webhook secret | Any random string (copy this) |
| Permissions | Pull requests: **Read & write**, Contents: **Read** |
| Subscribe to events | `Pull request`, `Installation` |

Click **Create GitHub App**. Copy the **App ID** and **Client ID**.

### 2. Generate a Private Key

In your GitHub App settings, scroll to **Private keys**, click **Generate a private key**, and save the `.pem` file.

```bash
chmod 600 /path/to/private-key.pem
```

### 3. Configure Environment

Create a `.env` file in the project root (or `bot/` directory):

```env
GITHUB_APP_ID=123456
GITHUB_CLIENT_ID=Iv1.xxxxxxxxxxxx
GITHUB_WEBHOOK_SECRET=your-webhook-secret
GITHUB_PRIVATE_KEY_PATH=certs/private-key.pem

# LLM provider (ollama or nvidia-nim)
LLM_PROVIDER=ollama
LLM_MODEL=qwen2.5-coder:7b
LLM_BASE_URL=http://localhost:11434
LLM_TIMEOUT_SECONDS=60
LLM_ENABLED=true
LLM_API_KEY=
HEURISTICS_ENABLED=true
AUTO_APPROVE=false
INLINE_COMMENTS=true
REVIEW_SUMMARY_ENABLED=true
```

The `.env` file is auto-loaded when the application starts — no need to `source .env`.

### 4. Build

```bash
cd bot
./mvnw clean package -DskipTests
```

The output JAR is `bot/target/bot-0.0.1-SNAPSHOT.jar`.

### 5. Run

```bash
java -jar bot/target/bot-0.0.1-SNAPSHOT.jar
```

You should see:
```
Tomcat started on port(s): 8080
```

### 6. Install the App on a Repository

In your GitHub App settings, go to **Install App**, select your repository, and click **Install**.

### 7. Test

Create a pull request on any repository the app is installed on. The bot will:

1. Receive the webhook (`POST /webhook/github`)
2. Verify the HMAC-SHA256 signature
3. Fetch the PR diff
4. Run heuristic analysis (± optional LLM analysis)
5. Post inline comments and a structured summary on the PR

---

## Configuration Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GITHUB_APP_ID` | Yes | — | GitHub App ID (numeric) |
| `GITHUB_CLIENT_ID` | Yes | — | GitHub App Client ID |
| `GITHUB_WEBHOOK_SECRET` | Yes | — | Webhook secret for HMAC-SHA256 verification |
| `GITHUB_PRIVATE_KEY_PATH` | No | `certs/private-key.pem` | Path to GitHub App private key (PKCS#1 or PKCS#8) |
| `LLM_PROVIDER` | No | `ollama` | LLM provider: `ollama` or `nvidia-nim` |
| `LLM_MODEL` | No | `qwen2.5-coder:7b` | Model name (passed to provider) |
| `LLM_BASE_URL` | No | `http://localhost:11434` | Provider API base URL |
| `LLM_TIMEOUT_SECONDS` | No | `60` | LLM request timeout |
| `LLM_ENABLED` | No | `true` | Enable/disable LLM analysis |
| `LLM_API_KEY` | No | — | API key for NVIDIA NIM (`nvapi-...`) |
| `HEURISTICS_ENABLED` | No | `true` | Enable/disable heuristics analysis |
| `AUTO_APPROVE` | No | `false` | Auto-approve PR if no issues found |
| `INLINE_COMMENTS` | No | `true` | Post inline line-level comments |
| `REVIEW_SUMMARY_ENABLED` | No | `true` | Post structured summary comment |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/webhook/github` | Main webhook handler (GitHub sends events here) |
| GET | `/webhook/health` | Simple health check |
| GET | `/actuator/health` | Spring Boot Actuator health endpoint |
| GET | `/actuator/info` | Application info |

---

## File Structure

```
bot/
├── src/main/java/com/bot/bot/
│   ├── BotApplication.java                  # Entry point, .env auto-loader
│   ├── config/
│   │   ├── AppProperties.java               # App-level settings
│   │   ├── GitHubProperties.java            # GitHub App config
│   │   ├── LLMProperties.java               # LLM/Ollama settings
│   │   ├── GsonConfig.java                  # JSON serialization
│   │   ├── WebClientConfig.java             # HTTP client configuration
│   │   ├── ReviewConfig.java                # .prreview.yaml model
│   │   └── RepoConfigLoader.java            # Fetches per-repo YAML
│   ├── webhook/
│   │   ├── GitHubWebhookController.java     # POST /webhook/github
│   │   └── WebhookSignatureVerifier.java     # HMAC-SHA256 verification
│   ├── github/
│   │   ├── GitHubApiClient.java             # All GitHub API calls + submitReview()
│   │   └── GitHubJwtGenerator.java          # JWT auth (PKCS#1 + PKCS#8)
│   ├── domain/
│   │   ├── PullRequestContext.java
│   │   ├── ChangeChunk.java
│   │   ├── Finding.java
│   │   └── ReviewComment.java
│   ├── diff/
│   │   └── UnifiedDiffParser.java           # Parses PR unified diffs
│   ├── analysis/
│   │   ├── Rule.java                        # Heuristic rule interface
│   │   ├── HeuristicsAnalysisEngine.java    # Runs all heuristic rules
│   │   ├── LLMReviewEngine.java             # Ollama-powered review
│   │   └── heuristics/
│   │       ├── SecretsDetectionRule.java
│   │       └── NullPointerDetectionRule.java
│   ├── llm/
│   │   └── OllamaClient.java                # HTTP client for Ollama API
│   ├── engine/
│   │   ├── FindingMerger.java               # Merges heuristic + LLM findings
│   │   └── ReviewPublisher.java             # submitReview() with inline comments
│   └── service/
│       └── ReviewOrchestrator.java          # Top-level review coordinator
├── src/main/resources/
│   └── application.yaml
├── pom.xml
├── Dockerfile
└── docker-compose.yml                       # Ollama + bot bundle
```

---

## Per-Repo Configuration

Repositories can include a `.prreview.yaml` file at the root. The bot fetches it from `raw.githubusercontent.com` and uses it to customize review behavior.

Example `.prreview.yaml`:

```yaml
rules:
  secrets:
    enabled: true
    severity: ERROR
  null-pointer:
    enabled: true
    severity: WARNING
llm:
  enabled: true
  model: qwen2.5-coder:7b
summary:
  auto-approve: true
  inline-comments: true
```

---

## Real-World Examples

### Hardcoded Secret

**PR code:**
```java
String password = "admin123";
```

**Bot response:** Inline comment on that line — `[FATAL] Hardcoded secret/credential detected. Use environment variables or a vault.`

### Null Pointer Risk

**PR code:**
```java
user.getEmail().toLowerCase()
```

**Bot response:** Inline comment — `[WARNING] Potential null pointer dereference: user.getEmail() could be null.`

### Structured Summary

After all inline comments, the bot posts a summary like:

```
## Review Summary

| Severity | Count |
|----------|-------|
| FATAL    | 1     |
| ERROR    | 0     |
| WARNING  | 2     |
| INFO     | 0     |

### Findings by File

- `src/main/java/example/UserService.java`: 3 findings
- `src/main/resources/config.yaml`: 1 secret detected
```

---

## Docker / Compose

### Single container

```bash
docker build -t pr-review-bot bot/
docker run -p 8080:8080 \
  -e GITHUB_APP_ID=123 \
  -e GITHUB_CLIENT_ID=Iv1.xxx \
  -e GITHUB_WEBHOOK_SECRET=xxx \
  -v /path/to/certs:/app/certs \
  pr-review-bot
```

### With Ollama (docker-compose)

```bash
cd bot
docker-compose up
```

This starts both Ollama and the bot. The bot connects to Ollama at `http://ollama:11434`.

---

## Troubleshooting

### Bot doesn't comment on PR

1. Is the app installed on the repository? Check **Settings > GitHub Apps** on the repo.
2. Is the webhook URL correct? Check **GitHub App Settings > Webhook URL**.
3. Is the server reachable? `curl https://your-domain.com/actuator/health`
4. Check **GitHub App Settings > Recent Deliveries** — look for `200` status codes.

### Signature validation errors

```
Fix:
1. Verify GITHUB_WEBHOOK_SECRET in .env matches the webhook secret in your GitHub App settings
2. Restart the bot
3. Resend a webhook delivery from GitHub's Recent Deliveries page
```

### "Installation not found"

```
Fix:
1. GitHub App Settings > Install App
2. Select your repository
3. Re-trigger the PR webhook
```

### Port 8080 in use

```bash
# Use a different port
java -jar bot/target/bot-0.0.1-SNAPSHOT.jar --server.port=9090
```

### Debug logging

```bash
export LOGGING_LEVEL_COM_BOT_BOT=DEBUG
java -jar bot/target/bot-0.0.1-SNAPSHOT.jar
```

---

## Success Checklist

- [ ] GitHub App created with pull request read/write and contents read permissions
- [ ] Private key generated and path set in `GITHUB_PRIVATE_KEY_PATH`
- [ ] `.env` file created with `GITHUB_APP_ID`, `GITHUB_CLIENT_ID`, `GITHUB_WEBHOOK_SECRET`
- [ ] Application built (`./mvnw clean package -DskipTests`)
- [ ] Application running (`java -jar bot/target/bot-0.0.1-SNAPSHOT.jar`)
- [ ] App installed on target repository
- [ ] First test PR created
- [ ] Bot posted inline comments and summary on the PR
- [ ] `GET /actuator/health` returns `{"status":"UP"}`

---

## Next Steps

1. **Tune the LLM** — try different Ollama models for better review quality
2. **Customize heuristics** — add new rules in `analysis/heuristics/`
3. **Add per-repo config** — create `.prreview.yaml` in repos to adjust severity levels
4. **Deploy** — use the Dockerfile or docker-compose for production
5. **Monitor** — check `/actuator/health` and logs for performance

---

**Ready to automate code review?** Start with the setup steps above and open your first test PR.
