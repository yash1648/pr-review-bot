# PR Review Bot

An intelligent, automated code review system that integrates with GitHub to provide real-time feedback on pull requests using static heuristics and local LLM inference.

## Features

- **Automated PR Reviews**: Triggered on PR open, update, or reopen
- **Dual Analysis Engine**: 
  - Fast static heuristics (secrets, null checks)
  - Deep LLM analysis (contextual code review)
- **Local LLM Support**: Privacy-preserving inference using Ollama
- **GitHub Native**: Publishes reviews as GitHub PR comments
- **Extensible**: Easy to add new heuristic rules
- **Production Ready**: Async processing, error handling, logging

## Architecture

The system follows a pipeline architecture with the following components:

1. **Webhook Layer**: Receives GitHub events, verifies HMAC-SHA256 signatures
2. **GitHub Integration**: Authenticates via GitHub App, fetches PR context and diffs
3. **Diff Processing**: Parses unified diff into structured change chunks
4. **Dual Analysis Engine**: 
   - Static heuristics (regex-based, fast, deterministic)
   - LLM review (contextual, thorough, using Ollama)
5. **Finding Merger**: Deduplicates and ranks findings by severity and confidence
6. **Review Publisher**: Posts formatted reviews to GitHub

## Quick Start

### Prerequisites

- **Java 21+** (with Maven 3.9+)
- **Ollama** installed locally
- **GitHub App** configured
- **Git** for cloning

### 1. Install Ollama & Model

```bash
# Install Ollama (macOS/Linux)
curl -fsSL https://ollama.com/install.sh | sh

# Or download from https://ollama.com

# Pull code review model
ollama pull qwen2.5-coder:7b

# Start Ollama server (runs on localhost:11434)
ollama serve
```

### 2. Create GitHub App

1. Go to GitHub Settings > Developer settings > GitHub Apps
2. Click "New GitHub App"
3. Fill in the form:
   - **App name**: pr-review-bot
   - **Homepage URL**: https://github.com/yourusername/pr-review-bot
   - **Webhook URL**: https://your-domain.com/webhook/github (set later with ngrok)
   - **Webhook secret**: Generate a secure random string

4. Set Permissions:
   - **Repository permissions**:
     - Pull requests: Read & Write
     - Contents: Read
   - **Subscribe to events**:
     - Pull request

5. Download your private key (`.pem` file)
6. Note your:
   - App ID
   - Client ID
   - Webhook Secret

### 3. Configure Application

```bash
# Clone the repository
git clone https://github.com/yourusername/pr-review-bot.git
cd pr-review-bot

# Create .env file
cp .env.example .env

# Edit .env with your GitHub App credentials
nano .env
```

**Example .env**:
```
GITHUB_APP_ID=12345
GITHUB_CLIENT_ID=Iv1.abcdef123456
GITHUB_WEBHOOK_SECRET=your-webhook-secret
GITHUB_PRIVATE_KEY_PATH=certs/github-app.pem
LLM_MODEL=qwen2.5-coder:7b
LLM_BASE_URL=http://localhost:11434
```

### 4. Set Up Private Key

```bash
# Create certs directory
mkdir -p certs

# Copy your GitHub App private key
cp ~/Downloads/pr-review-bot.pem certs/github-app.pem
```

### 5. Run Application

```bash
# Build and run
./mvnw clean spring-boot:run

# Application starts on http://localhost:8080
```

### 6. Expose Webhook (Local Development)

In another terminal:

```bash
# Install ngrok if needed
# Download from https://ngrok.com

# Start ngrok
ngrok http 8080

# You'll see: Forwarding https://abc123.ngrok.io -> http://localhost:8080
```

Update your GitHub App Webhook URL to: `https://abc123.ngrok.io/webhook/github`

### 7. Test It Out!

1. Create a test repository
2. Install the GitHub App to your repository
3. Open a pull request with some code changes
4. The bot should analyze and comment within seconds!

## Configuration

All configuration is handled via environment variables (or .env file):

| Variable | Description | Default |
|----------|-------------|---------|
| `GITHUB_APP_ID` | Your GitHub App ID | Required |
| `GITHUB_CLIENT_ID` | Your GitHub App Client ID | Required |
| `GITHUB_WEBHOOK_SECRET` | Webhook signature secret | Required |
| `GITHUB_PRIVATE_KEY_PATH` | Path to private key `.pem` file | `certs/github-app.pem` |
| `LLM_MODEL` | Ollama model name | `qwen2.5-coder:7b` |
| `LLM_BASE_URL` | Ollama API URL | `http://localhost:11434` |
| `LLM_TIMEOUT_SECONDS` | LLM request timeout | 60 |
| `LLM_ENABLED` | Enable/disable LLM analysis | true |
| `MAX_DIFF_SIZE_BYTES` | Maximum diff size to process | 1048576 (1MB) |
| `MAX_FILES_PER_PR` | Maximum files per PR to process | 50 |
| `HEURISTICS_ENABLED` | Enable/disable heuristic rules | true |
| `ENABLE_COMMENT_DELETION` | Delete old bot comments | false |

## Project Structure

```
pr-review-bot/
├── src/main/java/com/prbot/
│   ├── config/              # Configuration classes
│   │   ├── AppProperties.java
│   │   ├── GitHubProperties.java
│   │   ├── LLMProperties.java
│   │   ├── GsonConfig.java
│   │   └── WebClientConfig.java
│   ├── webhook/             # Webhook handling
│   │   ├── GitHubWebhookController.java
│   │   ├── GitHubWebhookPayload.java
│   │   └── WebhookSignatureVerifier.java
│   ├── github/              # GitHub API integration
│   │   ├── GitHubApiClient.java
│   │   └── GitHubJwtGenerator.java
│   ├── domain/              # Domain models
│   │   ├── PullRequestContext.java
│   │   ├── ChangeChunk.java
│   │   └── Finding.java
│   ├── diff/                # Diff parsing
│   │   └── UnifiedDiffParser.java
│   ├── analysis/            # Analysis engines
│   │   ├── Rule.java
│   │   ├── HeuristicsAnalysisEngine.java
│   │   └── LLMReviewEngine.java
│   ├── analysis/heuristics/ # Heuristic rules
│   │   ├── SecretsDetectionRule.java
│   │   └── NullPointerDetectionRule.java
│   ├── llm/                 # LLM integration
│   │   └── OllamaClient.java
│   ├── engine/              # Review processing
│   │   ├── FindingMerger.java
│   │   └── ReviewPublisher.java
│   ├── service/             # Business logic
│   │   └── ReviewOrchestrator.java
│   └── PrReviewBotApplication.java
├── src/main/resources/
│   └── application.yml      # Spring config
├── pom.xml                  # Maven configuration
├── .env.example             # Example environment file
└── README.md                # This file
```

## Adding Custom Rules

To add a new heuristic rule:

```java
@Component
public class MyCustomRule implements Rule {
    @Override
    public List<Finding> analyze(ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();
        
        // Your analysis logic here
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

Base URL (local development): `http://localhost:8080`

### `GET /webhook/health`

Simple liveness check.

```bash
curl http://localhost:8080/webhook/health
```

Response:

- Status: `200 OK`
- Body: `OK`

### `POST /webhook/github`

Inbound GitHub webhook endpoint for pull request events.

**Headers**

- `X-Hub-Signature-256`: HMAC SHA-256 signature of the raw request body using `GITHUB_WEBHOOK_SECRET`, formatted as `sha256=<hex-digest>`
- `X-GitHub-Event`: GitHub event type, this service processes `pull_request`
- `X-GitHub-Delivery`: Unique event ID from GitHub (for logging and traceability)
- `Content-Type`: `application/json`

**Authentication**

- The endpoint trusts GitHub as the caller
- Authentication is performed by verifying the `X-Hub-Signature-256` header against the payload using the shared `GITHUB_WEBHOOK_SECRET`
- Requests with invalid signatures are rejected with `401 Unauthorized`

**Supported Events**

- Event: `pull_request`
- Actions that trigger analysis:
  - `opened`
  - `synchronize`
  - `reopened`

Any other `pull_request` actions are acknowledged but ignored.

**Request Body**

Standard GitHub pull request webhook payload. The bot uses at least:

- `action`
- `repository.owner.login`
- `repository.name`
- `pull_request.number`
- `pull_request.title`
- `pull_request.body`
- `pull_request.user.login`
- `pull_request.base.ref.ref`
- `pull_request.head.ref.ref`
- `pull_request.head.sha`

**Responses**

- `202 Accepted` + body `"Processing started"`
  - Valid signature
  - Event: `pull_request`
  - Action: `opened`, `synchronize`, or `reopened`
  - Side effect: asynchronous review pipeline is started
- `200 OK` + body `"Event ignored"`
  - Valid signature
  - `X-GitHub-Event` is not `pull_request`
- `200 OK` + body `"Action ignored"`
  - Valid signature
  - Event is `pull_request` but `action` is not one of the supported ones
- `401 Unauthorized` + body `"Invalid signature"`
  - Signature verification failed
- `500 Internal Server Error` + body `"Error processing webhook"`
  - An unexpected exception occurred while parsing or dispatching the event

**Example: Valid PR Event**

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

Expected response:

- Status: `202 Accepted`
- Body: `Processing started`

## Testing

Run the full automated test suite:

```bash
cd bot
./mvnw.cmd test
```

This executes:

- Spring Boot context tests
- Unit tests for webhook handling and signature verification
- Unit tests for diff parsing and heuristic rules
- Unit tests for LLM review integration and finding merging
- Orchestration tests for the end-to-end review pipeline

## Deployment

### Docker

```dockerfile
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/pr-review-bot-1.0.0.jar app.jar
ENV GITHUB_APP_ID=${GITHUB_APP_ID}
ENV GITHUB_WEBHOOK_SECRET=${GITHUB_WEBHOOK_SECRET}
ENV LLM_BASE_URL=${LLM_BASE_URL}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
mvn clean package
docker build -t pr-review-bot:latest .
docker run -e GITHUB_APP_ID=xxx -e GITHUB_WEBHOOK_SECRET=xxx pr-review-bot:latest
```

### Kubernetes

See `docs/Deployment.md` for Kubernetes manifests and production setup guides.

## Monitoring

The application logs to console with timestamps and levels:

```
2024-01-15 10:23:45 - Starting PR review process
2024-01-15 10:23:46 - Processing PR owner/repo/#123
2024-01-15 10:23:47 - Parsed 5 change chunks
2024-01-15 10:23:48 - Running heuristics analysis
2024-01-15 10:23:49 - Heuristics found 2 findings
2024-01-15 10:23:50 - Running LLM analysis
```

Configure logging in `application.yml`:
```yaml
logging:
  level:
    com.prbot: DEBUG  # Set to DEBUG for more detailed logs
```

## Troubleshooting

### LLM Service Not Available

**Error**: "LLM service unavailable"

**Solution**: Ensure Ollama is running:
```bash
ollama serve
```

### Invalid Webhook Signature

**Error**: "Invalid webhook signature"

**Solution**: Verify `GITHUB_WEBHOOK_SECRET` matches GitHub App settings

### GitHub App Authentication Failed

**Error**: "Failed to generate JWT token"

**Solution**: 
1. Verify private key file exists at configured path
2. Ensure private key format is correct (PEM format)
3. Check App ID is correct

### Port Already in Use

**Error**: "Port 8080 is already in use"

**Solution**: 
```bash
# Use different port
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"

# Or kill process using port 8080
lsof -i :8080
kill -9 <PID>
```

## Performance

The system is optimized for performance:

- **Parallel Heuristic Analysis**: Changes analyzed in parallel across CPU cores
- **Async Webhook Processing**: Non-blocking PR processing
- **Configurable LLM Timeouts**: Prevents hanging on slow responses
- **Diff Size Limits**: Prevents memory issues with large diffs

Typical performance:
- Heuristics: ~100ms for small files
- LLM Analysis: ~5-30s depending on model and code size
- Total end-to-end: ~6-35s per PR

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## License

MIT License - see LICENSE file for details.

## Support

For issues, questions, or suggestions:
1. Check existing GitHub issues
2. Create a detailed issue with logs
3. Include your Java/Maven/Ollama versions

## Roadmap

- [ ] Support for multiple LLM providers (Claude, GPT-4)
- [ ] Custom rule marketplace
- [ ] Web UI for configuration
- [ ] Advanced metrics and analytics
- [ ] Slack/Discord notifications
- [ ] Multi-repository dashboards
