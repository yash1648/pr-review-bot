# 🚀 GitHub PR Review Bot - Getting Started Guide

**Time needed**: 30 minutes  
**Difficulty**: Beginner-friendly  
**Prerequisites**: GitHub account, Java 17+, Maven

---

## Table of Contents

1. [5-Minute Quick Start](#5-minute-quick-start)
2. [Detailed Setup](#detailed-setup)
3. [Test & Verify](#test--verify)
4. [Understanding the Flow](#understanding-the-flow)
5. [Common Issues](#common-issues)

---

## 5-Minute Quick Start

For experienced developers who want to skip details:

```bash
# 1. Clone and build
git clone <your-repo>
cd pr-review-bot
mvn clean package

# 2. Set environment variables
export GITHUB_APP_ID=123456
export GITHUB_PRIVATE_KEY_PATH=/path/to/key.pem
export GITHUB_WEBHOOK_SECRET=your-secret
# ... (more vars below)

# 3. Run
java -jar target/app.jar

# 4. Create a test PR on any repo
# Watch the bot comment! 🤖
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
   | Webhook secret | Generate random string (e.g., `openssl rand -hex 32`) |
   | Permissions (Pull Request) | ✅ Read & write |
   | Permissions (Contents) | ✅ Read |
   | Permissions (Check suites) | ✅ Read & write |
   | Subscribe to events | ✅ Pull request |
   | | ✅ Installation |

4. Click **"Create GitHub App"**
5. Save the **App ID** (you'll need this)

**1.2 Generate Private Key**

1. In your new app settings, go to **"Private keys"** section
2. Click **"Generate a private key"**
3. A `.pem` file downloads automatically
4. Save it somewhere safe: `/secure/path/github-private-key.pem`
5. Set permissions: `chmod 600 /secure/path/github-private-key.pem`

**1.3 Get OAuth Credentials**

1. Go to **"Client secrets"** section
2. Click **"Generate a new client secret"**
3. Save the secret somewhere safe (you'll only see it once)
4. Copy the **Client ID** (starts with `Iv1.`)

**1.4 Install App on Repository**

1. Go to **"Install App"** in the sidebar
2. Click **"Install"** next to your repository name
3. Select the repo or organization
4. Click **"Install"**
5. Note the **Installation ID** from the URL (after `/installations/`)

**✅ After Phase 1, you have:**
- App ID
- Private key file
- Client ID
- Client Secret
- Webhook secret

---

### Phase 2: Local Configuration (5 minutes)

**2.1 Clone & Prepare Repository**

```bash
# Clone the repository
git clone <your-repo-url>
cd pr-review-bot

# Create .env file
cp .env.example .env
```

**2.2 Edit .env File**

Open `.env` and fill in:

```bash
# From Phase 1:
GITHUB_APP_ID=123456                    # Your numeric App ID
GITHUB_PRIVATE_KEY_PATH=/path/to/key.pem # Full path to .pem file
GITHUB_CLIENT_ID=Iv1.xxxxxxxxxxxxx     # OAuth Client ID
GITHUB_CLIENT_SECRET=ghp_xxxxx          # OAuth Client Secret
GITHUB_WEBHOOK_SECRET=random_string     # Your webhook secret

# For local testing:
GITHUB_REDIRECT_URI=http://localhost:8080/webhook/github/authorize
```

**2.3 Load Environment Variables**

```bash
# Option 1: Load from .env (Linux/Mac)
source .env

# Option 2: Set manually (all platforms)
export GITHUB_APP_ID=123456
export GITHUB_PRIVATE_KEY_PATH=/path/to/key.pem
# ... etc
```

**2.4 Verify Configuration**

```bash
# Check if private key is readable
cat $GITHUB_PRIVATE_KEY_PATH | head -1
# Should show: -----BEGIN RSA PRIVATE KEY-----

# Check if App ID is set
echo $GITHUB_APP_ID
# Should show: 123456
```

**✅ After Phase 2:**
- All credentials configured locally

---

### Phase 3: Build Application (5 minutes)

**3.1 Build with Maven**

```bash
# Clean and build
mvn clean package -DskipTests

# Should end with:
# [INFO] BUILD SUCCESS
# [INFO] Total time:  45.234 s

# JAR file created at:
ls -lh target/app.jar
```

**3.2 (Alternative) Build with Docker**

```bash
# Option A: Build Docker image
docker build -t pr-review-bot:latest .

# Option B: Use Docker Compose
docker-compose build
```

**✅ After Phase 3:**
- Application built and ready to run

---

### Phase 4: Run Application (2 minutes)

**4.1 Start Locally**

```bash
# Load environment variables
source .env

# Run the JAR
java -jar target/app.jar

# You should see:
# Started Application in X seconds
# [main] o.s.b.w.e.tomcat.TomcatWebServer : 
#   Tomcat started on port(s): 8080

# App is now running! ✅
```

**4.2 (Alternative) Start with Docker**

```bash
# Option A: Docker
docker run -e GITHUB_APP_ID=$GITHUB_APP_ID \
  -e GITHUB_PRIVATE_KEY_PATH=$GITHUB_PRIVATE_KEY_PATH \
  -e GITHUB_WEBHOOK_SECRET=$GITHUB_WEBHOOK_SECRET \
  pr-review-bot:latest

# Option B: Docker Compose
docker-compose up
```

**4.3 Verify It's Running**

```bash
# In another terminal:
curl http://localhost:8080/webhook/github

# You should get:
# 400 Bad Request (no auth header)
# This is GOOD - it means the endpoint exists!
```

**✅ App is running on http://localhost:8080**

---

## Test & Verify

### Step 1: Create Test PR

**Local Development:**
Since your local machine isn't accessible from GitHub, use ngrok to tunnel:

```bash
# Install ngrok: https://ngrok.com
# Then tunnel your port:
ngrok http 8080

# Copy the ngrok URL (https://xxxxx.ngrok.io)
# Update webhook URL in GitHub App settings to:
# https://xxxxx.ngrok.io/webhook/github
```

**Or Use a Server:**
If you have a public server, update GitHub App webhook URL to:
```
https://your-server.com:8080/webhook/github
```

### Step 2: Create PR to Test

1. **Push to GitHub**
   ```bash
   git checkout -b test/bot-review
   echo "// Test code" > test.java
   git add test.java
   git commit -m "Test PR for bot"
   git push origin test/bot-review
   ```

2. **Open Pull Request**
   - Go to GitHub repository
   - Click "Compare & pull request"
   - Create the PR

3. **Watch the Magic**
   - You should see:
     - 🤖 "Starting code review analysis..." comment
     - Review with findings (if any issues)
     - Inline comments on changed lines

### Step 3: Check Logs

```bash
# Look for these messages:
tail -f logs/application.log

# Should see:
# ✓ "Received pull_request event"
# ✓ "Starting analysis for PR"
# ✓ "Analysis complete"
# ✓ "Published review with X findings"
```

### Step 4: View in GitHub

1. Go to your PR
2. Scroll to **"Checks"** or **"Files Changed"** tab
3. See the bot's review with:
   - Summary statistics
   - Inline comments on problem lines
   - Confidence scores
   - Suggestions for fixes

---

## Understanding the Flow

### What Happens When You Create a PR

```
Timeline (with actual durations):

[GitHub] → [Your Server] → [Analysis] → [Review Posted]
    ↓            ↓              ↓             ↓
  0ms        <100ms     30 sec-2 min      <5 sec

Detailed flow:

1. [0ms] User creates PR on GitHub
         ↓
2. [10ms] GitHub sends webhook with PR details
          ↓
3. [50ms] Your server receives webhook
          - GithubWebhookController validates signature
          - Routes to PullRequestEventHandler
          - Returns 200 OK to GitHub (important!)
          ↓
4. [100ms] Analysis starts in background (async)
           - Fetches PR metadata
           - Downloads diff
           - Posts "analyzing..." comment
           ↓
5. [15-90 sec] Code analysis runs
               - StaticAnalysisEngine checks Java code
               - LLMReview calls AI (if configured)
               - FindingMerger combines results
               ↓
6. [120 sec] Review published
             - ReviewPublisher creates GitHub review
             - Posts inline comments
             - Updates summary comment
             ↓
7. [Complete] User sees review on PR 🎉
```

### Component Interactions

```
User Creates PR
    ↓
GitHub Webhook
    ↓
┌─────────────────────────────┐
│ GithubWebhookController     │
│ - Validates signature       │
│ - Extracts PR details       │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│ GithubWebhookProcessor      │
│ - Routes pull_request event │
└──────────────┬──────────────┘
               ↓
┌─────────────────────────────┐
│ PullRequestEventHandler     │
│ - Fetches PR context        │
│ - Calls AnalysisEngine      │
│ - Calls ReviewPublisher     │
└──────────────┬──────────────┘
               ↓
    ┌──────────┴──────────┐
    ↓                     ↓
┌──────────────────┐  ┌────────────────────┐
│ AnalysisEngine   │  │ ReviewPublisher    │
│ - StaticAnalysis │  │ - Posts review     │
│ - LLM analysis   │  │ - Inline comments  │
│ - Merge findings │  │ - Summary stats    │
└──────────────────┘  └────────────────────┘
    ↓                     ↓
└──────────────┬──────────┘
               ↓
           GitHub PR
        (with review)
```

---

## Common Issues

### ❌ Webhook Not Triggering

**Problem**: You create a PR but no bot comment appears.

**Checklist**:
1. ✓ App installed on repository?
   ```bash
   Go to repo Settings → GitHub Apps
   Your app should be listed
   ```

2. ✓ Webhook URL correct?
   ```bash
   GitHub App Settings → Webhook URL
   Should be: https://your-domain.com/webhook/github
   ```

3. ✓ Server is reachable?
   ```bash
   curl https://your-domain.com/webhook/github
   Should respond (even with 400 error is OK)
   ```

4. ✓ Check Recent Deliveries
   ```bash
   GitHub App Settings → Webhooks → Recent Deliveries
   Look for 200 status codes
   ```

**Solution**:
```bash
# If using ngrok locally:
1. ngrok http 8080
2. Copy ngrok URL (e.g., https://abc123.ngrok.io)
3. Update GitHub App → Webhook URL to:
   https://abc123.ngrok.io/webhook/github
4. Create test PR
```

---

### ❌ Signature Validation Error

**Problem**: "Invalid webhook signature"

**Cause**: Webhook secret mismatch

**Solution**:
```bash
# 1. Check GitHub App webhook secret
# Settings → Webhooks → Secret field

# 2. Check your environment variable
echo $GITHUB_WEBHOOK_SECRET

# 3. Must match exactly!
# If not, update environment variable:
export GITHUB_WEBHOOK_SECRET=correct_secret

# 4. Restart application
pkill -f "java -jar"
java -jar target/app.jar
```

---

### ❌ "Installation Not Found"

**Problem**: Error in logs: "Installation ID not found"

**Cause**: App not installed on repository

**Solution**:
1. Go to your GitHub App settings
2. Click "Install App"
3. Select your repository
4. Click "Install"
5. Note the Installation ID
6. Create a new PR

---

### ❌ Private Key Error

**Problem**: "Unable to load private key"

**Cause**: Private key path wrong or permissions issue

**Solution**:
```bash
# 1. Verify file exists and is readable
cat $GITHUB_PRIVATE_KEY_PATH | head -1
# Should show: -----BEGIN RSA PRIVATE KEY-----

# 2. Check permissions
ls -l $GITHUB_PRIVATE_KEY_PATH
# Should be: -rw------- (600)
chmod 600 $GITHUB_PRIVATE_KEY_PATH

# 3. Verify path is correct
echo $GITHUB_PRIVATE_KEY_PATH
# Should be full path, not ~/path or $HOME/path

# 4. Restart app
```

---

### ❌ Port 8080 Already in Use

**Problem**: "Address already in use: bind"

**Solution**:
```bash
# Option 1: Use different port
java -jar target/app.jar --server.port=9090

# Option 2: Kill existing process
# macOS/Linux:
lsof -i :8080
kill -9 <PID>

# Windows:
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

---

### ❌ Analysis Taking Too Long

**Problem**: Analysis takes >5 minutes

**Solution**:
```properties
# In application.properties, increase thread pool:
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=20

# Or if using LLM, check API rate limits:
# - OpenAI: 3 requests/min on free tier
# - Claude: Check your plan
```

---

## 🎉 You're Ready!

After completing all steps:

✅ GitHub App is created and installed  
✅ Environment variables are configured  
✅ Application is built and running  
✅ Webhook is receiving requests  
✅ First PR review was posted  

**Next Steps**:

1. **Fine-tune**: Adjust analysis severity levels based on your needs
2. **Customize**: Add custom analysis rules specific to your codebase
3. **Deploy**: Move to production server or cloud platform
4. **Monitor**: Track PR quality trends and bot accuracy
5. **Integrate**: Connect to Slack, JIRA, or other tools

---

## 📖 More Documentation

- **Quick Reference**: See `QUICK_REFERENCE.md`
- **Full Usage Guide**: See `USAGE_GUIDE.md`
- **Component Architecture**: See `COMPONENT_OVERVIEW.md`
- **Setup Details**: See `GITHUB_AUTH_SETUP.md`

---

## 💬 Need Help?

1. **Check logs**:
   ```bash
   tail -f logs/application.log | grep ERROR
   ```

2. **Test endpoint**:
   ```bash
   curl http://localhost:8080/webhook/github -v
   ```

3. **Verify GitHub connection**:
   ```bash
   curl -H "Authorization: token YOUR_TOKEN" \
     https://api.github.com/app
   ```

4. **Review GitHub App webhooks**:
   - Settings → Webhooks → Recent Deliveries
   - Look for failed (non-200) requests

---

**Status**: ✅ Ready to launch!  
**Version**: 1.0  
**Last Updated**: 2024

Happy reviewing! 🚀
