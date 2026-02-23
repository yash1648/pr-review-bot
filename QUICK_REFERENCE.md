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
export GITHUB_CLIENT_SECRET=your_secret
export GITHUB_WEBHOOK_SECRET=your_webhook_secret
export GITHUB_REDIRECT_URI=https://your-domain.com/webhook/github/authorize
```

### Step 3: Start Application
```bash
mvn spring-boot:run
# Port: 8080
# Webhook endpoint: POST /webhook/github
```

### Step 4: Create Test PR
```bash
1. Create new branch
2. Make code changes
3. Push to GitHub
4. Open Pull Request
5. Watch for bot comment 🤖
```

---

## 📋 What Each Class Does

| Class | Purpose |
|-------|---------|
| `GithubAppConfig` | Holds all GitHub credentials |
| `GithubJwtGenerator` | Creates auth tokens (JWT) |
| `GithubAuthService` | Gets authenticated GitHub clients |
| `GithubWebhookController` | Receives webhooks from GitHub |
| `GithubWebhookProcessor` | Routes events to handlers |
| `PullRequestEventHandler` | Orchestrates analysis pipeline |
| `ReviewPublisher` | Posts reviews to GitHub |
| `StaticAnalysisEngine` | Analyzes code for issues |
| `DiffParser` | Extracts changed code from PR |
| `Finding` | Represents one code issue |

---

## 🔧 Configuration

**File**: `application.properties`
```properties
github.app.app-id=${GITHUB_APP_ID}
github.app.private-key-path=${GITHUB_PRIVATE_KEY_PATH}
github.app.client-id=${GITHUB_CLIENT_ID}
github.app.client-secret=${GITHUB_CLIENT_SECRET}
github.app.webhook-secret=${GITHUB_WEBHOOK_SECRET}
```

**Override defaults**:
```bash
java -jar app.jar --GITHUB_APP_ID=123 --GITHUB_WEBHOOK_SECRET=xyz
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

### Security Issues 🔒
- ✓ Hardcoded passwords/secrets
- ✓ SQL injection risks
- ✓ Insecure crypto

### Quality Issues 👨‍💻
- ✓ Null pointer risks
- ✓ Resource leaks
- ✓ Empty catch blocks
- ✓ Large methods

### Best Practices 🎯
- ✓ Proper null checks
- ✓ Logging practices
- ✓ Exception handling

---

## 🐛 Debugging

**Enable debug logging**:
```properties
logging.level.com.bot.bot=DEBUG
```

**Check if webhook received**:
```bash
# In logs, look for:
tail -f logs/application.log | grep "Received pull_request"
```

**Verify GitHub App connection**:
```bash
# Should see app details
curl -H "Authorization: token YOUR_TOKEN" \
  https://api.github.com/app
```

**Test auth manually**:
```bash
# Visit endpoint
curl http://localhost:8080/webhook/github
# Should get 400 (missing auth header)
```

---

## ⚠️ Common Issues & Fixes

| Problem | Solution |
|---------|----------|
| Webhook not triggering | Check URL is public (not localhost) |
| Signature validation fails | Webhook secret mismatch |
| "Installation not found" | Reinstall app on repository |
| Slow analysis | Increase thread pool size in properties |
| Port 8080 in use | `java -jar app.jar --server.port=9090` |
| Private key not found | Check path is correct and readable |
| 401 Unauthorized | Verify GITHUB_APP_ID and private key |

---

## 🎮 Usage Scenarios

### Scenario 1: First Time Setup
```
1. Follow GitHub Setup section
2. Set environment variables
3. mvn spring-boot:run
4. Open GitHub → your app settings → Recent Deliveries
5. Should see 200 status code
6. Create test PR
7. Check PR for bot comment
```

### Scenario 2: Customize Analysis Rules
```
File: StaticAnalysisEngine.java

Add new check:
- Copy existing check method (e.g., checkSecurityIssues)
- Modify regex/conditions
- Add to analyze() method
- Test on PR
```

### Scenario 3: Deploy to Production
```
1. Build JAR: mvn clean package
2. Set env vars in production environment
3. java -jar target/app.jar
4. Or use Docker (see USAGE_GUIDE.md)
```

### Scenario 4: Monitor Performance
```
In logs, look for:
- "Starting analysis for PR" → analysis started
- "Analysis complete. Found X findings" → done
- "Published review with" → review posted

Check duration between these timestamps
```

---

## 📈 Performance Tips

| Tip | Benefit |
|-----|---------|
| Increase thread pool | Analyze multiple PRs simultaneously |
| Cache tokens 55 min | Reduce GitHub API calls |
| Skip non-Java files | Faster analysis |
| Filter low confidence | Fewer false positives |

---

## 🔐 Security Checklist

- [ ] Private key file permissions: 600
- [ ] Never commit .pem file
- [ ] Use HTTPS for webhook URL
- [ ] Validate all webhook signatures
- [ ] Rotate private key yearly
- [ ] Monitor API token usage
- [ ] Use env vars for secrets
- [ ] Log security events

---

## 📞 Getting Help

**Check logs first**:
```bash
# Enable full debugging
logging.level.root=DEBUG

# Filter for errors
grep ERROR logs/application.log
```

**Common log messages**:
```
✓ "Received pull_request event" → Webhook arrived
✓ "Starting analysis for PR" → Analysis began
✓ "Analysis complete" → Analysis finished
✗ "Invalid webhook signature" → Secret mismatch
✗ "Installation not found" → Reinstall app
```

**Test each piece**:
```bash
# 1. Can connect to GitHub?
curl https://api.github.com

# 2. Is webhook endpoint reachable?
curl http://localhost:8080/webhook/github

# 3. Can generate JWT?
# Add test endpoint in controller

# 4. Can create review?
# Add test endpoint to publish review
```

---

## 🎯 Next Steps

After setup works:

1. **Fine-tune rules** - Adjust severity levels
2. **Add custom checks** - Add domain-specific analysis
3. **Monitor PRs** - Watch quality trends
4. **Scale up** - Deploy to production
5. **Integrate** - Connect to Slack/JIRA

---

## 📖 Full Documentation

- **Setup**: See `GITHUB_AUTH_SETUP.md`
- **Architecture**: See `COMPONENT_OVERVIEW.md`
- **Complete Guide**: See `USAGE_GUIDE.md`
- **Code**: See individual Java files

---

## 🚀 Quick Commands

```bash
# Build
mvn clean package

# Run
java -jar target/app.jar

# Run with custom port
java -jar target/app.jar --server.port=9090

# Run with debug logging
java -jar target/app.jar --logging.level.com.bot.bot=DEBUG

# Build Docker image
docker build -t pr-review-bot .

# Run Docker
docker run -e GITHUB_APP_ID=123 pr-review-bot
```

---

**Status**: ✅ Ready to use!
**Last Updated**: 2024
**Version**: 1.0
