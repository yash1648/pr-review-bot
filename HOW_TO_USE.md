# 📋 HOW TO USE YOUR GITHUB PR REVIEW BOT

## TL;DR (Too Long; Didn't Read)

```bash
# 1. Create GitHub App at https://github.com/settings/apps
# 2. Copy credentials to .env file
# 3. mvn clean package
# 4. java -jar target/app.jar
# 5. Create a PR on GitHub
# 6. Bot reviews your PR automatically 🤖
```

---

## 📚 Complete Documentation

### For First-Time Users
👉 **START HERE**: Read `GETTING_STARTED.md`
- Step-by-step setup (30 minutes)
- Create GitHub App
- Configure environment
- Test with first PR
- Troubleshooting

### For Quick Reference
👉 **Quick Answers**: `QUICK_REFERENCE.md`
- Common commands
- Configuration table
- Debugging checklist
- Performance tips

### For Deep Understanding
👉 **Full Details**: `USAGE_GUIDE.md`
- How it works with diagrams
- Real-world examples
- Customization guide
- Production deployment
- API integration
- Monitoring

### For Architecture
👉 **System Design**: `COMPONENT_OVERVIEW.md`
- Component interactions
- Authentication flows
- Security features
- Performance notes

### For Setup
👉 **Technical Setup**: `GITHUB_AUTH_SETUP.md`
- GitHub App creation
- Permission configuration
- Webhook setup
- Security best practices

---

## 🎯 What Your Bot Does

### Analyzes Code for Issues
```
✓ Security issues (hardcoded secrets, SQL injection)
✓ Null pointer risks
✓ Resource leaks
✓ Exception handling problems
✓ Code quality issues
✓ Best practice suggestions
```

### Posts Reviews on Pull Requests
```
✓ Summary statistics
✓ Inline comments on problem lines
✓ Severity levels (Fatal, Error, Warning, Info)
✓ Helpful suggestions for fixes
✓ Confidence scores
```

### Works Automatically
```
✓ Triggered on every PR (opened/updated)
✓ Runs asynchronously (non-blocking)
✓ Caches tokens for performance
✓ Validates webhook signatures (security)
```

---

## ⚙️ How It Works

### Simple Version
```
PR Created → Bot Analyzes → Bot Comments
    ↓            ↓              ↓
 GitHub      Your Server    GitHub PR
```

### Detailed Version
```
1. User creates PR on GitHub
   ↓
2. GitHub sends webhook to your server
   ↓
3. Your server validates the webhook (security check)
   ↓
4. Analysis starts in background (doesn't block webhook)
   ↓
5. Bot fetches changed code from the PR
   ↓
6. Analyzes for issues (static analysis + optional AI)
   ↓
7. Posts review with findings on the PR
   ↓
8. User sees bot review + inline comments
```

---

## 📁 File Structure

```
your-project/
├── src/main/java/com/bot/bot/
│   ├── config/
│   │   ├── GithubAppConfig.java       # Configuration holder
│   │   └── AppConfig.java             # Spring config
│   ├── engine/
│   │   ├── Finding.java               # Issue data model
│   │   ├── AnalysisEngine.java        # Main analyzer
│   │   ├── analysis/
│   │   │   └── StaticAnalysisEngine.java  # Code checking
│   │   └── parser/
│   │       └── DiffParser.java        # Parse PR diffs
│   └── github/
│       ├── auth/
│       │   ├── GithubAuthService.java      # Authentication
│       │   └── GithubJwtGenerator.java     # Token generation
│       ├── webhook/
│       │   ├── GithubWebhookController.java   # Receive webhooks
│       │   ├── GithubWebhookProcessor.java    # Process events
│       │   └── PullRequestEventHandler.java   # Handle PRs
│       └── ReviewPublisher.java      # Post reviews
│
├── src/main/resources/
│   └── application.properties         # Configuration
│
├── Documentation/
│   ├── GETTING_STARTED.md            # 👈 START HERE
│   ├── QUICK_REFERENCE.md            # Quick answers
│   ├── USAGE_GUIDE.md                # Full guide
│   ├── COMPONENT_OVERVIEW.md         # Architecture
│   └── GITHUB_AUTH_SETUP.md          # Setup details
│
├── Deployment/
│   ├── Dockerfile                    # Docker container
│   ├── docker-compose.yml            # Docker Compose
│   ├── setup.sh                      # Automated setup
│   └── .env.example                  # Environment template
│
└── pom.xml                           # Maven dependencies
```

---

## 🚀 Quick Start (Copy-Paste)

### Step 1: Create GitHub App
```
1. Go to https://github.com/settings/apps
2. Click "New GitHub App"
3. Fill form:
   - App name: pr-review-bot
   - Webhook URL: https://your-domain.com/webhook/github
   - Webhook secret: (any random string)
   - Permissions:
     ✓ Pull Request (read & write)
     ✓ Contents (read)
   - Events:
     ✓ Pull request
     ✓ Installation
4. Click "Create GitHub App"
5. Copy: App ID
```

### Step 2: Generate Private Key
```
1. In GitHub App settings
2. Scroll to "Private keys"
3. Click "Generate a private key"
4. Save the .pem file
5. Run: chmod 600 /path/to/key.pem
```

### Step 3: Get OAuth Credentials
```
1. In GitHub App settings
2. Go to "Client secrets"
3. Click "Generate a new client secret"
4. Copy: Client ID (Iv1.xxx) and Secret
```

### Step 4: Configure Your App
```bash
# Create .env file
cp .env.example .env

# Edit .env with your values:
GITHUB_APP_ID=your_app_id
GITHUB_PRIVATE_KEY_PATH=/path/to/key.pem
GITHUB_CLIENT_ID=Iv1.xxx
GITHUB_CLIENT_SECRET=your_secret
GITHUB_WEBHOOK_SECRET=any_random_string
GITHUB_REDIRECT_URI=https://your-domain.com/webhook/github/authorize
```

### Step 5: Build & Run
```bash
# Load environment
source .env

# Build
mvn clean package

# Run
java -jar target/app.jar

# Should see:
# Tomcat started on port(s): 8080
```

### Step 6: Install App on Repository
```
1. GitHub App settings → "Install App"
2. Select your repository
3. Click "Install"
```

### Step 7: Test It
```
1. Create new branch: git checkout -b test-pr
2. Make a change: echo "test" > test.txt
3. Push: git push origin test-pr
4. Open PR on GitHub
5. Watch bot comment appear! 🎉
```

---

## 🔍 What to Look For

### In GitHub (PR Page)
```
✓ Bot posts initial comment: "🤖 Starting code review analysis..."
✓ Bot creates a review with findings
✓ Inline comments appear on problem lines
✓ Summary shows statistics (X critical, Y errors, etc.)
```

### In Logs (Your Terminal)
```bash
grep "Starting analysis" logs/application.log
# Should show: "Starting analysis for PR owner/repo #1"

grep "Analysis complete" logs/application.log
# Should show: "Analysis complete. Found 5 findings"

grep "Published review" logs/application.log
# Should show: "Published review with 5 findings"
```

### In GitHub App Settings
```
Settings → Webhooks → Recent Deliveries
✓ Should see pull_request events with 200 status
✓ If red X, webhook failed (check logs for why)
```

---

## ⚠️ Troubleshooting

### Bot Doesn't Comment on PR
```
Check:
1. Is GitHub App installed on the repo?
   → Repo Settings → GitHub Apps → Your app listed?
   
2. Is webhook URL correct?
   → GitHub App Settings → Webhook URL
   
3. Is server reachable?
   → curl https://your-domain.com/webhook/github
   
4. Check recent deliveries
   → GitHub App Settings → Webhooks → Recent Deliveries
   → Should see 200 status codes
```

### Signature Validation Error
```
Fix:
1. Copy webhook secret from GitHub App settings
2. Update GITHUB_WEBHOOK_SECRET in .env
3. Restart the app
4. Create test PR
```

### "Installation Not Found"
```
Fix:
1. GitHub App Settings → Install App
2. Select your repository
3. Click Install
4. Create test PR
```

### Port 8080 in Use
```
Fix:
# Option 1: Different port
java -jar target/app.jar --server.port=9090

# Option 2: Kill process on 8080
lsof -i :8080
kill -9 <PID>
```

---

## 🎓 Learning Path

### Beginner (First-time users)
1. Read: `GETTING_STARTED.md`
2. Follow all steps
3. Create first test PR
4. Verify bot works

### Intermediate (Want to customize)
1. Read: `QUICK_REFERENCE.md`
2. Read: `USAGE_GUIDE.md` → Customization section
3. Modify `StaticAnalysisEngine.java`
4. Add your own analysis rules

### Advanced (Deep dive)
1. Read: `COMPONENT_OVERVIEW.md`
2. Read: `GITHUB_AUTH_SETUP.md`
3. Understand authentication flow
4. Add LLM integration
5. Connect to other services

---

## 📊 Real-World Examples

### Example 1: Hardcoded Secret
**Code added to PR:**
```java
String password = "admin123";
```

**Bot Response:**
```
🚨 [FATAL] Hardcoded secret/credential detected
Line 42
Suggestion: Use environment variables or vaults
```

### Example 2: Resource Leak
**Code added to PR:**
```java
Scanner scanner = new Scanner(System.in);
```

**Bot Response:**
```
❌ [ERROR] Resource allocated without cleanup
Line 15
Suggestion: Use try-with-resources: try (Scanner s = new Scanner(...)) {...}
```

### Example 3: Null Pointer Risk
**Code added to PR:**
```java
user.getEmail().toLowerCase()
```

**Bot Response:**
```
⚠️ [WARNING] Potential null pointer dereference
Line 28
Suggestion: Add null check: if (user.getEmail() != null)
```

---

## 🚢 Deployment Options

### Local (Development)
```bash
mvn spring-boot:run
# Port: 8080
```

### Docker
```bash
docker build -t pr-review-bot .
docker run -e GITHUB_APP_ID=123 pr-review-bot
```

### Docker Compose
```bash
docker-compose up
```

### AWS
```bash
# Build JAR
mvn clean package

# Upload to EC2/Lambda/ECS
# Set environment variables in platform
# Start application
```

### Kubernetes
```bash
# See docker-compose.yml for Kubernetes manifests
kubectl apply -f deployment.yaml
```

---

## 📞 Support

### Documentation
- `GETTING_STARTED.md` - Step-by-step guide
- `QUICK_REFERENCE.md` - Quick answers
- `USAGE_GUIDE.md` - Complete guide
- `COMPONENT_OVERVIEW.md` - Architecture

### Common Issues
See "Troubleshooting" section above

### Debug Mode
```bash
export LOGGING_LEVEL_COM_BOT_BOT=DEBUG
java -jar target/app.jar
```

### Check Logs
```bash
tail -f logs/application.log | grep ERROR
```

---

## ✨ Features

### Security Analysis
```
✓ Detects hardcoded credentials
✓ Finds SQL injection risks
✓ Identifies insecure crypto
✓ Checks for common vulnerabilities
```

### Code Quality
```
✓ Identifies null pointer risks
✓ Finds resource leaks
✓ Detects empty catch blocks
✓ Suggests best practices
```

### Performance
```
✓ Async processing (non-blocking)
✓ Token caching (fast)
✓ Signature validation (secure)
✓ Scalable architecture
```

---

## 🎉 Success Checklist

- [ ] GitHub App created
- [ ] Private key downloaded
- [ ] Environment variables set
- [ ] Application built (mvn clean package)
- [ ] Application running (java -jar...)
- [ ] Webhook URL set in GitHub App
- [ ] App installed on repository
- [ ] First test PR created
- [ ] Bot commented on PR
- [ ] Inline comments visible

✅ **All checked? You're ready!**

---

## 🔗 Next Steps

After successful setup:

1. **Fine-tune** - Adjust severity levels
2. **Customize** - Add custom analysis rules
3. **Monitor** - Track PR quality trends
4. **Deploy** - Move to production
5. **Integrate** - Connect to Slack/JIRA

---

## 📌 Key Takeaways

```
✓ Bot automatically reviews PRs
✓ Runs on your server (you control data)
✓ Configurable analysis rules
✓ Secure webhook signature validation
✓ Fast async processing
✓ Works with GitHub out of the box
```

---

**Ready to review code like a pro?** 🚀

Start with `GETTING_STARTED.md` →
