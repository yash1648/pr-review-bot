# GitHub PR Review Bot - Quick Reference

## 🚀 Start Here (in order)

### Step 1: GitHub Setup (5 min)
```bash
1. https://github.com/settings/apps → New GitHub App
2. App name: "PR Review Bot"
3. Webhook URL: https://your-domain.com/webhook/github
4. Webhook secret: [generate + save]
5. Permissions: PR (read & write), Contents (read)
6. Events: pull_request, installation
7. Private keys → Generate + download .pem
8. Install → Select repo → Authorize
```

### Step 2: Set Environment Variables
```bash
export GITHUB_APP_ID=your_app_id
export GITHUB_PRIVATE_KEY_PATH=/path/to/key.pem
export GITHUB_CLIENT_ID=your_client_id
export GITHUB_WEBHOOK_SECRET=your_webhook_secret
export AUTO_APPROVE=false
export INLINE_COMMENTS=true
```

### Step 3: Build & Start
```bash
cd bot
./mvnw clean package -DskipTests
java -jar target/bot-0.0.1-SNAPSHOT.jar
# Port: 8080
# Webhook: POST /webhook/github
# Health: GET /webhook/health or /actuator/health
```

### Step 4: Create Test PR
```bash
1. Create new branch
2. Make code changes
3. Push to GitHub
4. Open Pull Request
5. Watch for bot comment
```

---

## 📋 What Each Class Does

| Class | Purpose |
|-------|---------|
| `GitHubProperties` | Holds GitHub App credentials |
| `GitHubJwtGenerator` | Creates auth tokens (JWT) |
| `GitHubApiClient` | Communicates with GitHub REST API |
| `GitHubWebhookController` | Receives webhooks from GitHub |
| `WebhookSignatureVerifier` | Validates webhook payloads |
| `ReviewOrchestrator` | Orchestrates the analysis pipeline |
| `HeuristicsAnalysisEngine` | Static analysis via regex rules |
| `SecretsDetectionRule` | Detects hardcoded secrets/tokens |
| `NullPointerDetectionRule` | Detects null pointer risks |
| `LLMReviewEngine` | AI-powered review via Ollama |
| `UnifiedDiffParser` | Extracts changed code from PR diffs |
| `Finding` | Represents one code issue |
| `FindingMerger` | Merges duplicate/similar findings |
| `ReviewPublisher` | Posts reviews to GitHub (with inline comments) |
| `RepoConfigLoader` | Loads per-repo `.prreview.yaml` config |

---

## 🔧 Configuration

**File**: `bot/src/main/resources/application.yaml`
```yaml
github:
  app-id: ${GITHUB_APP_ID}
  client-id: ${GITHUB_CLIENT_ID}
  webhook-secret: ${GITHUB_WEBHOOK_SECRET}
  private-key-path: ${GITHUB_PRIVATE_KEY_PATH:certs/private-key.pem}
  api-url: https://api.github.com

llm:
  model: ${LLM_MODEL:qwen2.5-coder:7b}
  base-url: ${LLM_BASE_URL:http://localhost:11434}
  enabled: ${LLM_ENABLED:true}

app:
  heuristics-enabled: ${HEURISTICS_ENABLED:true}
  auto-approve: ${AUTO_APPROVE:false}
  inline-comments: ${INLINE_COMMENTS:true}
  review-summary-enabled: ${REVIEW_SUMMARY_ENABLED:true}
```

**Per-repo override** (`.prreview.yaml` in target repo's default branch):
```yaml
enabled: true
auto_approve: false
inline_comments: true
review_summary: true
llm_model: gpt-4
ignore_paths:
  - "*.md"
  - "test/"
ignore_rules:
  - "secrets-detection"
```

**Override on startup**:
```bash
java -jar target/bot-0.0.1-SNAPSHOT.jar --GITHUB_APP_ID=123 --AUTO_APPROVE=true
```

---

## 🧪 Test Webhook Locally

```bash
# 1. Get your webhook secret
SECRET="your-webhook-secret"

# 2. Create test payload
PAYLOAD='{"action":"opened","pull_request":{"number":1},"installation":{"id":123}}'

# 3. Generate signature
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.* /sha256=/')

# 4. Send request
curl -X POST http://localhost:8080/webhook/github \
  -H "X-Hub-Signature-256: $SIGNATURE" \
  -H "X-GitHub-Event: pull_request" \
  -H "X-GitHub-Delivery: test-123" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"
```

---

## 📊 What Bot Reviews

### Security Issues
- Hardcoded passwords/secrets/tokens
- SQL injection risks
- Insecure crypto patterns

### Quality Issues
- Null pointer risks
- Resource leaks
- Empty catch blocks
- Large methods / high complexity

### Best Practices
- Proper null checks
- Logging practices
- Exception handling

---

## ✨ Features

| Feature | Description | Env/Config |
|---------|-------------|------------|
| **Inline Comments** | Line-level comments on PR diffs | `INLINE_COMMENTS=true` |
| **Auto-Approve** | Approves PR when no issues found | `AUTO_APPROVE=true` |
| **LLM Review** | AI-powered review via Ollama | `LLM_ENABLED=true` |
| **Heuristics** | Regex-based static analysis | `HEURISTICS_ENABLED=true` |
| **Per-Repo Config** | `.prreview.yaml` overrides globals | See config section |
| **Health Checks** | Readiness & liveness probes | `/webhook/health` + `/actuator/health` |

---

## 🐛 Debugging

**Enable debug logging** (default in `application.yaml`):
```yaml
logging.level.com.bot.bot=DEBUG
```

**Check if webhook received**:
```bash
tail -f logs/application.log | grep "Received pull_request"
```

**Verify health**:
```bash
curl http://localhost:8080/webhook/health
# → OK

curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

**Test auth manually**:
```bash
curl http://localhost:8080/webhook/github
# → 400 (missing headers — expected)
```

---

## ⚠️ Common Issues & Fixes

| Problem | Solution |
|---------|----------|
| Webhook not triggering | Check URL is public (not localhost) |
| Signature validation fails | Webhook secret mismatch |
| "Installation not found" | Reinstall app on repository |
| Slow analysis | Check Ollama is running / reachable |
| Port 8080 in use | `--server.port=9090` |
| Private key not found | Check path is correct and readable |
| 401 Unauthorized | Verify GITHUB_APP_ID and private key |
| `.prreview.yaml` not loading | Check file is in default branch's root |

---

## 🎮 Usage Scenarios

### Scenario 1: First Time Setup
```
1. Follow GitHub Setup section
2. Set environment variables
3. ./mvnw clean package -DskipTests && java -jar target/bot-0.0.1-SNAPSHOT.jar
4. Open GitHub → your app settings → Recent Deliveries
5. Should see 200 status code
6. Create test PR
7. Check PR for bot comment + inline annotations
```

### Scenario 2: Customize Analysis Rules
```
File: HeuristicsAnalysisEngine.java

Add new rule:
- Create a class implementing `Rule` interface
- Register it in the engine
- Test on a PR
```

### Scenario 3: Per-Repo Tuning
```
Add .prreview.yaml to your repo's default branch:

auto_approve: true
ignore_paths: ["*.md", "docs/*"]
llm_model: codellama:13b

The bot loads this on every PR automatically.
```

### Scenario 4: Deploy to Production
```
1. Build JAR: ./mvnw clean package -DskipTests
2. Set env vars in production environment
3. java -jar target/bot-0.0.1-SNAPSHOT.jar
4. Or use Docker (see docker-compose.yml)
```

---

## 📈 Performance Tips

| Tip | Benefit |
|-----|---------|
| Cache JWT tokens (55 min) | Reduce GitHub API calls |
| Skip non-code files | Faster analysis |
| Disable LLM if not needed | `LLM_ENABLED=false` — heuristics only |
| Filter low-confidence findings | Fewer false positives |
| Use `.prreview.yaml` ignore_paths | Skip generated/vendor files |

---

## 🔐 Security Checklist

- [ ] Private key file permissions: 600
- [ ] Never commit .pem file
- [ ] Use HTTPS for webhook URL
- [ ] Validate all webhook signatures
- [ ] Rotate private key yearly
- [ ] Use env vars for secrets (not hardcoded)
- [ ] Review `.prreview.yaml` before adding to repos

---

## 🚀 Quick Commands

```bash
cd bot

# Build
./mvnw clean package -DskipTests

# Run
java -jar target/bot-0.0.1-SNAPSHOT.jar

# Run with custom port
java -jar target/bot-0.0.1-SNAPSHOT.jar --server.port=9090

# Run with debug logging
java -jar target/bot-0.0.1-SNAPSHOT.jar --logging.level.com.bot.bot=DEBUG

# Build Docker image
docker build -t pr-review-bot bot/

# Run Docker
docker run -e GITHUB_APP_ID=123 -e GITHUB_PRIVATE_KEY_PATH=/key.pem pr-review-bot
```

---

## 📖 Full Documentation

- **Setup**: See `GITHUB_AUTH_SETUP.md`
- **Architecture**: See `COMPONENT_OVERVIEW.md`
- **Config**: See `bot/src/main/resources/application.yaml`

---

**Status**: ✅ Ready to use
**Last Updated**: 2026-06
**Version**: 0.0.1-SNAPSHOT
