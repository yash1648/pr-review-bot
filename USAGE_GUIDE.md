# GitHub PR Review Bot - Complete Usage Guide

## Quick Start (5 minutes)

### 1. Prerequisites
- GitHub account
- Your repository
- Spring Boot application deployed
- All classes implemented

### 2. Create GitHub App
```bash
1. Go to https://github.com/settings/apps
2. Click "New GitHub App"
3. Fill form:
   - App name: "PR Review Bot"
   - Homepage URL: https://your-domain.com
   - Webhook URL: https://your-domain.com/webhook/github
   - Webhook secret: Generate random string (save it!)
```

### 3. Generate Private Key
```bash
1. In GitHub App settings → "Private keys"
2. Click "Generate a private key"
3. Save the .pem file securely
4. Copy App ID from the top
```

### 4. Configure Permissions
In GitHub App settings:

**Repository Permissions:**
- Pull Requests: Read & write ✓
- Contents: Read ✓
- Commit statuses: Read & write ✓

**Events to Subscribe:**
- ✓ Pull request
- ✓ Pull request review
- ✓ Installation

### 5. Install App on Repository
```bash
1. Go to GitHub App settings
2. Click "Install App" (left sidebar)
3. Select your repository
4. Authorize
```

### 6. Set Environment Variables
```bash
# Linux/Mac
export GITHUB_APP_ID=123456
export GITHUB_PRIVATE_KEY_PATH=/path/to/private-key.pem
export GITHUB_CLIENT_ID=Iv1.xxxxx
export GITHUB_CLIENT_SECRET=your_secret
export GITHUB_WEBHOOK_SECRET=your_webhook_secret
export GITHUB_REDIRECT_URI=https://your-domain.com/webhook/github/authorize

# Windows (PowerShell)
$env:GITHUB_APP_ID="123456"
$env:GITHUB_PRIVATE_KEY_PATH="C:\path\to\private-key.pem"
# ... etc
```

### 7. Start Your Spring Boot App
```bash
mvn spring-boot:run
# Or if already compiled:
java -jar application.jar
```

### 8. Verify Webhook
```bash
1. Go to GitHub App settings
2. Scroll to "Webhooks"
3. Click "Recent Deliveries"
4. Should see successful delivery (200 status)
```

### 9. Test It!
```bash
1. Create a new branch
2. Make code changes (intentional issues help testing)
3. Push to GitHub
4. Create a Pull Request
5. Watch the magic happen! 🚀
```

---

## How It Works

### Flow Diagram
```
User creates PR
    ↓
GitHub sends webhook
    ↓
Your webhook endpoint receives it
    ↓
GithubWebhookController validates signature
    ↓
GithubWebhookProcessor routes to PullRequestEventHandler
    ↓
PullRequestEventHandler:
  - Gets authenticated GitHub client
  - Fetches PR diff
  - Posts "analyzing..." comment
    ↓
AnalysisEngine runs:
  - StaticAnalysis (heuristics)
  - LLMReview (AI analysis)
  - FindingMerger (combines results)
    ↓
ReviewPublisher posts GitHub review with findings
    ↓
GitHub displays review on PR
```

### What Happens When PR is Created/Updated

1. **Webhook Received** (< 1 second)
   - GitHub sends webhook to your app
   - Signature validated
   - Request returns 200 OK immediately

2. **Analysis Starts** (async, background)
   - Fetches PR metadata
   - Downloads diff
   - Parses changed code
   - Posts initial comment

3. **Code Analysis** (30 seconds - 2 minutes)
   - **Static Analysis**: Checks for security, null pointers, resources
   - **LLM Analysis**: Calls AI for intelligent code review
   - **Merge**: Combines findings, removes duplicates

4. **Review Published** (< 5 seconds)
   - Creates official GitHub review
   - Posts inline comments on changed lines
   - Shows summary statistics

---

## Real-World Examples

### Example 1: Security Issue Detection

**Your Code (added in PR):**
```java
String password = "admin123";
String query = "SELECT * FROM users WHERE id=" + userId;
```

**Bot Response:**
```
🚨 Critical Issues Found

**Summary**: 2 critical, 0 errors, 0 warnings

### Findings

🚨 [FATAL] Hardcoded secret/credential detected
Line 42: String password = "admin123";
Suggestion: Never commit secrets. Use environment variables or vaults

🚨 [FATAL] Potential SQL injection
Line 43: String query = "SELECT * FROM users WHERE id=" + userId;
Suggestion: Use parameterized queries: 
  PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id=?");
  ps.setInt(1, userId);
```

### Example 2: Code Quality Issues

**Your Code:**
```java
public void processData(List<Data> items) {
    // 500+ lines of code
    if (items == null) {} // empty catch
    // more code
}
```

**Bot Response:**
```
⚠️ [WARNING] Empty catch block - exception silently ignored
Line 15
Suggestion: Add logging or handle the exception properly

💡 [INFO] Large method - consider breaking into smaller methods
Line 5
Suggestion: This method is very long. Extract into multiple smaller methods for readability
```

### Example 3: Good Code Suggestion

**Your Code:**
```java
if (user != null && user.getProfile() != null) {
    return user.getProfile().getName();
}
```

**Bot Response:**
```
✅ No critical issues found

💡 [INFO] Good null checking practices observed
Suggestion: Consider using Optional for even better null handling:
  return user.map(User::getProfile)
            .map(Profile::getName)
            .orElse(null);
```

---

## Monitoring & Debugging

### Check Webhook Deliveries
```bash
curl -H "Authorization: token YOUR_GITHUB_TOKEN" \
  https://api.github.com/app/installations/INSTALL_ID/events
```

### View App Logs
```bash
# If running locally
tail -f logs/app.log

# If in Docker
docker logs container-name

# Look for these patterns:
# ✓ "Starting analysis for PR"
# ✓ "Analysis complete. Found X findings"
# ✓ "Published review with"
```

### Common Issues

**❌ Webhook not triggering?**
```
1. Check webhook URL is publicly accessible
   curl https://your-domain.com/webhook/github
   Should respond with 200 or 400 (not 404)

2. Verify webhook secret matches
   In GitHub: Settings → Webhooks
   In your app: GITHUB_WEBHOOK_SECRET env var

3. Check GitHub App is installed on repo
   Go to repo Settings → GitHub Apps
   Should see your app listed
```

**❌ Signature validation fails?**
```
Error: Invalid webhook signature

Solution:
- Webhook secret must match exactly
- Check for extra spaces/newlines
- Verify header name: X-Hub-Signature-256
```

**❌ "Installation not found" error?**
```
Error: Installation ID 12345 not found

Solution:
1. Reinstall app on repository
2. Copy new Installation ID from webhook payload
3. Verify in GitHub App: Install → Recent deliveries
```

**❌ Analysis hangs or times out?**
```
Solution:
1. Check AnalysisEngine is properly configured
2. If using LLM, verify API key and rate limits
3. Increase timeout in application.properties:
   spring.task.execution.pool.queue-capacity=200
```

---

## Customization

### Change Analysis Rules

**File**: `StaticAnalysisEngine.java`

```java
// Add new check:
private List<Finding> checkLoggingPractices(String filepath, String[] lines, Map<Integer, String> changedLines) {
    List<Finding> findings = new ArrayList<>();
    
    for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        
        // Check for System.out.println (bad)
        if (line.contains("System.out.println")) {
            findings.add(Finding.builder()
                .severity(Finding.Severity.WARNING)
                .message("Use logger instead of System.out.println")
                .source("heuristic")
                .confidence(0.95)
                .suggestions("Replace with: logger.info(...)")
                .build());
        }
    }
    return findings;
}

// Then call it in analyze():
findings.addAll(checkLoggingPractices(filepath, code, changedLines));
```

### Adjust Severity Levels

```java
// In Finding.java - change what's FATAL vs WARNING
// Default (stricter):
FATAL   → Security issues, hardcoded secrets
ERROR   → Resource leaks, SQL injection
WARNING → Null pointer risks, empty catch
INFO    → Best practices, suggestions

// To be less strict:
FATAL   → Hardcoded secrets only
ERROR   → SQL injection
WARNING → Resource leaks, null pointers
```

### Filter by File Type

```java
// Only analyze certain files
if (!filepath.endsWith(".java")) {
    return new ArrayList<>(); // Skip non-Java files
}

// Or only test files
if (filepath.contains("Test.java")) {
    // Different rules for tests
}
```

---

## API Integration

### Manually Trigger Analysis
```bash
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "installationId": 12345,
    "fullRepoName": "owner/repo",
    "prNumber": 1,
    "headSha": "abc123def456"
  }'
```

### Get Analysis Results
```bash
curl http://localhost:8080/api/findings/owner/repo/1
```

### Dismiss Finding
```bash
curl -X POST http://localhost:8080/api/findings/dismiss \
  -d '{"findingId": "uuid-here"}'
```

---

## Production Deployment

### Docker
```dockerfile
FROM openjdk:17-jdk-slim
COPY target/app.jar app.jar
ENV GITHUB_APP_ID=${GITHUB_APP_ID}
ENV GITHUB_PRIVATE_KEY_PATH=/secrets/key.pem
ENTRYPOINT ["java","-jar","app.jar"]
```

```bash
docker run -e GITHUB_APP_ID=123 \
  -e GITHUB_WEBHOOK_SECRET=secret \
  -v /secrets/key.pem:/secrets/key.pem \
  your-app:latest
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
    spec:
      containers:
      - name: bot
        image: your-registry/pr-review-bot:latest
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
          value: /etc/secrets/key.pem
        volumeMounts:
        - name: github-key
          mountPath: /etc/secrets
      volumes:
      - name: github-key
        secret:
          secretName: github-private-key
```

### AWS Lambda
```java
// Use spring-cloud-function for serverless
@Component
public class WebhookFunction implements Function<String, String> {
    @Override
    public String apply(String payload) {
        // Process webhook
        return "OK";
    }
}
```

---

## Monitoring

### Key Metrics to Track
```
- Webhooks received per hour
- Analysis success rate
- Average analysis time
- False positive rate
- Critical findings per PR
- User satisfaction (👍 reactions)
```

### Set Up Alerts
```bash
# If failures spike
if (failed_analysis_count > 10 in 5_minutes) {
    send_alert_to_slack()
}

# If too slow
if (analysis_duration > 5_minutes) {
    send_alert_to_slack()
}
```

---

## Performance Tips

### Optimize for Speed
```properties
# Increase async thread pool
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=20

# Cache tokens longer (be careful with security)
# In GithubAuthService: cache for 55 minutes instead of 50

# Skip analysis for certain files
# In PullRequestEventHandler: exclude .json, .xml files
```

### Reduce False Positives
```java
// Increase confidence thresholds
if (finding.confidence() < 0.8) {
    continue; // Skip low-confidence findings
}

// Filter by category
if ("documentation".equals(finding.category())) {
    continue; // Skip doc changes
}
```

---

## Troubleshooting Checklist

- [ ] GitHub App created and installed
- [ ] Private key file exists and readable
- [ ] All environment variables set
- [ ] App is running (port 8080 open)
- [ ] Webhook URL is public (not localhost)
- [ ] Created a test PR on a branch
- [ ] Check GitHub App Recent Deliveries for 200 status
- [ ] Look for "Starting analysis" in logs
- [ ] Review appears on the PR

---

## Getting Help

### Check Logs
```bash
# Enable debug logging
logging.level.com.bot.bot=DEBUG

# Search for:
# - "Received pull_request event"
# - "Starting analysis"
# - "Published review"
# - Any ERROR or WARN messages
```

### Test Each Component
```bash
# Test auth
curl -H "Authorization: token YOUR_TOKEN" \
  https://api.github.com/app

# Test webhook endpoint
curl -X POST http://localhost:8080/webhook/github \
  -H "X-GitHub-Event: push" \
  -d '{}'

# Test GitHub client
GithubAuthService authService = context.getBean(GithubAuthService.class);
GitHub github = authService.getAppClient();
System.out.println(github.getAppInstallations());
```

---

## Next Steps

1. **Monitor** - Watch PRs and check review quality
2. **Tune** - Adjust severity levels based on your team's preferences
3. **Extend** - Add custom analysis rules specific to your codebase
4. **Integrate** - Connect with Slack, JIRA, or other tools
5. **Scale** - Deploy to production with monitoring

Enjoy your automated code reviews! 🚀
