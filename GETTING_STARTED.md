# GitHub PR Review Bot - Getting Started Guide

**Time needed**: 30 minutes  
**Difficulty**: Beginner-friendly  
**Prerequisites**: GitHub account, Java 21+, Maven 3.8+

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Detailed Setup](#detailed-setup)
3. [Test & Verify](#test--verify)
4. [Understanding the Flow](#understanding-the-flow)
5. [Common Issues](#common-issues)

---

## Quick Start

For experienced developers who want to skip the details:

```bash
# 1. Clone and build
cd bot
./mvnw clean package -DskipTests

# 2. Copy and edit .env with your GitHub App credentials
cp .env.example .env

# 3. Run
java -jar target/bot-0.0.1-SNAPSHOT.jar

# 4. Create a test PR on any repo
# Watch the bot comment!
```

---

## Detailed Setup

### Phase 1: GitHub App Creation (5 minutes)

**1.1 Create GitHub App**

1. Open https://github.com/settings/apps
2. Click **"New GitHub App"** button
3. Fill in the form:

   | Field | Value |
   |-------|-------|
   | App name | `pr-review-bot` |
   | Homepage URL | `https://your-domain.com` |
   | Webhook URL | `https://your-domain.com/webhook/github` |
   | Webhook secret | Generate random string (`openssl rand -hex 32`) |
   | Permissions (Pull Request) | Read & write |
   | Permissions (Contents) | Read |
   | Permissions (Check suites) | Read & write |
   | Subscribe to events | Pull request |
   | | Installation |

4. Click **"Create GitHub App"**
5. Save the **App ID** (you'll need this)

**1.2 Generate Private Key**

1. In your new app settings, go to **"Private keys"** section
2. Click **"Generate a private key"**
3. A `.pem` file downloads automatically
4. Save it somewhere safe: `/secure/path/github-private-key.pem`
5. Set permissions: `chmod 600 /secure/path/github-private-key.pem`

**1.3 Save Client ID**

1. Copy the **Client ID** from your app settings (starts with `Iv1.`)
2. No client secret is needed -- the bot authenticates via JWT tokens (App Installation)

**1.4 Install App on Repository**

1. Go to **"Install App"** in the sidebar
2. Click **"Install"** next to your repository name
3. Select the repo or organization
4. Click **"Install"**

---

### Phase 2: Local Configuration (5 minutes)

**2.1 Clone & Prepare**

```bash
git clone <your-repo-url>
cd pr-review-bot/bot
cp .env.example .env
```

**2.2 Edit .env File**

Open `.env` and fill in:

```bash
GITHUB_APP_ID=123456                   # From app settings
GITHUB_CLIENT_ID=Iv1.xxxxxxxxxxxxx     # OAuth Client ID
GITHUB_PRIVATE_KEY_PATH=/path/to/key.pem  # Full path to .pem file
GITHUB_WEBHOOK_SECRET=your_webhook_secret  # From app creation step
```

The `.env` file is loaded automatically at startup -- no need to `source .env`.

**2.3 Optional Variables**

These are set to sensible defaults but can be overridden in `.env`:

```bash
# Analysis control
AUTO_APPROVE=false              # Auto-approve PRs with no findings
INLINE_COMMENTS=true            # Add inline comments on problem lines
HEURISTICS_ENABLED=true         # Run pattern-based analysis
LLM_ENABLED=true                # Run LLM analysis (requires Ollama)

# LLM provider (ollama or nvidia-nim)
LLM_PROVIDER=ollama
LLM_MODEL=qwen2.5-coder:7b
LLM_BASE_URL=http://localhost:11434
LLM_TIMEOUT_SECONDS=60
LLM_API_KEY=
```

**2.4 Verify Configuration**

```bash
# Check if private key is readable
head -1 $GITHUB_PRIVATE_KEY_PATH
# Should show: -----BEGIN RSA PRIVATE KEY-----

# Check if App ID is set
echo $GITHUB_APP_ID
# Should show: 123456
```

---

### Phase 3: Build Application (5 minutes)

```bash
# Navigate to the bot directory (where pom.xml lives)
cd bot

# Clean build (skips tests for speed)
./mvnw clean package -DskipTests

# Expected output:
# [INFO] BUILD SUCCESS
# [INFO] Total time:  45.234 s

# Verify the JAR was created
ls -lh target/bot-0.0.1-SNAPSHOT.jar
```

---

### Phase 4: Run Application (2 minutes)

```bash
# Environment variables are auto-loaded from .env
java -jar target/bot-0.0.1-SNAPSHOT.jar

# Expected startup output:
# Started BotApplication in X seconds
# Tomcat started on port(s): 8080
```

**Verify it's running:**

```bash
curl http://localhost:8080/webhook/health
# Should return: OK

curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

---

## Test & Verify

### Step 1: Make the Bot Reachable

Since your local machine isn't accessible from GitHub, use ngrok to create a tunnel:

```bash
# Install ngrok: https://ngrok.com
ngrok http 8080

# Copy the ngrok URL (https://xxxxx.ngrok.io)
# Update webhook URL in GitHub App settings to:
# https://xxxxx.ngrok.io/webhook/github
```

### Step 2: Create a Test PR

```bash
git checkout -b test/bot-review
echo "// Test code" > test.java
git add test.java
git commit -m "Test PR for bot"
git push origin test/bot-review
```

1. Go to your GitHub repository
2. Click **"Compare & pull request"**
3. Create the PR

### Step 3: Watch the Bot Respond

The bot will:

1. Receive the webhook event
2. Fetch the diff and analyze it
3. Post a review with findings (or auto-approve if configured)

Check the bot's terminal logs:

```
Received GitHub webhook event: pull_request
Processing PR owner/repo/#123
Parsed 5 change chunks
Heuristics found 3 findings
Review published successfully for owner/repo/#123
```

### Step 4: View the Review

Go to your PR on GitHub:

- **Files Changed** tab -- see inline comments on specific lines
- **Conversation** tab -- see the summary review body
- If enabled, the bot's check suite shows the review status

---

## Understanding the Flow

### High-Level Architecture

```
GitHub Webhook Event
    |
    v
GitHubWebhookController     Validates signature, dispatches PR events
    |
    v
ReviewOrchestrator          Coordinates the full review pipeline (async)
    |
    +-- GitHubApiClient      Fetches PR metadata and diff from GitHub API
    |      +-- GitHubJwtGenerator  Generates JWT tokens for API auth
    |
    +-- HeuristicsAnalysisEngine  Pattern-based rules (secrets, null pointers, etc.)
    |      +-- Rule implementations (SecretsDetectionRule, NullPointerDetectionRule, ...)
    |
    +-- LLMReviewEngine      AI-powered analysis via Ollama
    |
    +-- FindingMerger        Deduplicates and ranks findings
    |
    +-- ReviewPublisher      Builds review summary and inline comments
           +-- GitHubApiClient  Submits review via GitHub API
```

### What Happens When You Create a PR

```
[1] User creates/updates PR on GitHub
       |
[2] GitHub sends webhook POST to /webhook/github
       |
[3] GitHubWebhookController:
    - Validates X-Hub-Signature-256
    - Returns 202 Accepted immediately
       |
[4] ReviewOrchestrator (async):
    - GitHubApiClient fetches PR context + diff
    - RepoConfigLoader loads .prreview.yaml (per-repo overrides)
    - UnifiedDiffParser parses the diff into ChangeChunks
       |
[5] Analysis phase:
    - HeuristicsAnalysisEngine runs pattern rules in parallel
    - LLMReviewEngine calls Ollama (if enabled) for AI review
    - FindingMerger deduplicates and ranks all findings
       |
[6] Review published:
    - ReviewPublisher builds summary + inline comments
    - GitHubApiClient.submitReview() posts to GitHub
    - Auto-approves if enabled and no findings found
```

### Per-Repo Configuration (`.prreview.yaml`)

Add a `.prreview.yaml` to the default branch of any repo to override global settings:

```yaml
# .prreview.yaml
enabled: true
auto_approve: false
inline_comments: true
llm_model: qwen2.5-coder:7b
ignore_paths:
  - "*.lock"
  - "vendor/*"
ignore_rules:
  - "secrets"
```

---

## Common Issues

### Webhook Not Triggering

**Problem**: PR created but no bot response.

**Checklist**:
1. App installed on repository? (Repo Settings > GitHub Apps)
2. Webhook URL correct in GitHub App settings? Should end in `/webhook/github`
3. Server reachable from GitHub? (ngrok running, no firewall blocks)
4. Check Recent Deliveries in GitHub App settings -- look for 200/202 responses

### Signature Validation Error

**Problem**: "Invalid signature" in logs.

**Solution**: The webhook secret must match exactly between GitHub App settings and your `.env`.

```bash
# Verify your env variable
echo $GITHUB_WEBHOOK_SECRET

# Update in GitHub App Settings > Webhook > Secret
# Then restart the bot
```

### Private Key Error

**Problem**: "Unable to load private key".

**Solution**:
```bash
# Verify file exists and has correct format
head -1 $GITHUB_PRIVATE_KEY_PATH
# Should show: -----BEGIN RSA PRIVATE KEY-----

# Check permissions
chmod 600 $GITHUB_PRIVATE_KEY_PATH

# Must use full absolute path, not ~/path
echo $GITHUB_PRIVATE_KEY_PATH
```

### Port 8080 Already in Use

**Problem**: "Address already in use: bind".

**Solution**:
```bash
# Use a different port
java -jar target/bot-0.0.1-SNAPSHOT.jar --server.port=9090

# Or kill the existing process
lsof -i :8080
kill -9 <PID>
```

### LLM Analysis Failing

**Problem**: LLM review engine errors in logs.

**Solution**:
- Ensure Ollama is running: `ollama list`
- Verify the model is pulled: `ollama pull qwen2.5-coder:7b`
- Check `LLM_BASE_URL` -- for local Ollama, use `http://localhost:11434`
- Set `LLM_ENABLED=false` to run heuristics-only (no AI required)

---

## You're Ready

After completing all steps:

- GitHub App is created and installed
- Environment variables are configured
- Application is built and running
- Webhook is receiving requests
- First PR review was posted

**Next Steps**:

1. **Fine-tune** -- adjust analysis settings via `application.yaml` or `.prreview.yaml`
2. **Add custom rules** -- implement new `Rule` classes for project-specific patterns
3. **Deploy** -- move to a production server or cloud platform
4. **Integrate LLM** -- configure Ollama or another provider for AI-powered reviews

---

## More Documentation

- **Quick Reference**: See `QUICK_REFERENCE.md`
- **Full Usage Guide**: See `USAGE_GUIDE.md`
- **Component Architecture**: See `COMPONENT_OVERVIEW.md`
- **Setup Details**: See `GITHUB_AUTH_SETUP.md`

---

## Need Help?

1. **Check logs**:
   ```bash
   tail -f logs/application.log | grep ERROR
   ```

2. **Test the webhook endpoint**:
   ```bash
   curl -v http://localhost:8080/webhook/health
   curl -v http://localhost:8080/actuator/health
   ```

3. **Review GitHub App webhook deliveries**:
   Settings > Webhooks > Recent Deliveries
   Look for failed (non-200) requests

---

**Status**: Ready to launch  
**Version**: 2.0  
**Last Updated**: 2026
