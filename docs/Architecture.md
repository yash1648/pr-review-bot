# Architecture Documentation

## Overview

PR Review Bot is a Spring Boot application that automates code reviews for GitHub pull requests. It combines fast static analysis with deep LLM-based review to provide comprehensive feedback.

## System Components

### 1. Webhook Layer

**Responsibility**: Receive and validate GitHub webhook events

**Key Classes:**
- `GitHubWebhookController`: REST controller handling POST /webhook/github
- HMAC-SHA256 signature verification
- Event filtering (only processes pull_request events)

**Security:**
- Webhook secrets prevent spoofing
- Signature verification using `X-Hub-Signature-256` header

### 2. GitHub Integration

**Responsibility**: Authenticate and communicate with GitHub API

**Key Classes:**
- `GitHubAuthService`: JWT generation for GitHub App, installation token management
- `PullRequestClient`: Fetches PR metadata and unified diff
- `ReviewPublisher`: Submits review comments via GitHub API

**Authentication Flow:**
1. Generate JWT using private key (valid for 10 minutes)
2. Exchange JWT for installation access token
3. Use installation token for API calls
4. Cache tokens until expiry (55 minutes)

### 3. Diff Processing

**Responsibility**: Parse Git unified diff format into structured data

**Key Classes:**
- `UnifiedDiffParser`: Regex-based parser for diff format
- `ChangeChunk`: Immutable record representing a diff hunk

**Parsing Strategy:**
- Extract file metadata (old/new paths, change type)
- Parse hunk headers (`@@ -start,count +start,count @@`)
- Separate added, removed, and context lines
- Skip binary files and non-Java files (configurable)

### 4. Analysis Engine

#### 4.1 Static Heuristics

**Responsibility**: Fast, deterministic code analysis

**Key Classes:**
- `Rule` interface: Contract for all heuristic rules
- `HardcodedSecretRule`: Detects secrets using regex patterns
- `NullDereferenceRule`: Identifies potential NPEs

**Characteristics:**
- Execution time: <10ms per chunk
- No external dependencies
- 100% deterministic
- Low false positive rate for secrets, medium for NPEs

#### 4.2 LLM Review

**Responsibility**: Contextual, intelligent code analysis

**Key Classes:**
- `LLMClient`: HTTP client for Ollama API
- `ReviewPromptBuilder`: Constructs prompts with context
- `LLMReviewEngine`: Orchestrates inference and parsing

**Prompt Strategy:**
- Include file context (surrounding code)
- Provide specific diff hunk
- Request structured JSON output
- Instruct to focus on bugs/security, ignore style

**Model Recommendations:**
- `qwen2.5-coder:7b`: Fast, good for Java
- `deepseek-coder:6.7b`: Excellent reasoning
- `codellama:7b-code`: Good general performance

### 5. Finding Merger

**Responsibility**: Combine and deduplicate findings from multiple sources

**Algorithm:**
1. Group findings by file path + line number
2. For conflicts, keep highest severity
3. Combine messages from different sources
4. Sort by severity (critical first), then file/line

### 6. Review Publisher

**Responsibility**: Format and submit findings to GitHub

**Features:**
- Summary comment with statistics
- Inline comments on specific lines
- Severity-based emoji indicators
- Source attribution (heuristic/LLM)

## Data Flow

See [Sequence Diagram](diagrams/sequence-diagram.png) for detailed flow.

1. GitHub sends webhook → Webhook Controller
2. Verify signature → Queue for processing
3. Fetch PR context (metadata + diff)
4. Parse diff into chunks
5. Run heuristics and LLM analysis in parallel
6. Merge findings
7. Publish review to GitHub

## Technology Choices

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Framework | Spring Boot 3.x | Mature, excellent GitHub library support |
| Language | Java 21 | Records, pattern matching, modern GC |
| GitHub API | github-api (Kohsuke) | Battle-tested, covers all features |
| HTTP Client | WebClient | Non-blocking for LLM calls |
| LLM Runtime | Ollama | Easy setup, local inference, OpenAI-compatible API |
| Build Tool | Maven | Standard for Spring, good IDE support |

## Scalability Considerations

### Current Limitations
- Single instance (no horizontal scaling)
- In-memory token cache
- Synchronous processing per PR

### Future Improvements
- Redis for distributed token cache
- Message queue (RabbitMQ/SQS) for webhook processing
- Separate workers for LLM inference
- Database for persistence and analytics

## Security Architecture

### Secrets Management
- Private key file mounted as secret
- Environment variables for App ID and webhook secret
- No secrets in code or logs

### Network Security
- Webhook signature verification
- TLS for all GitHub API calls
- Local LLM (no data leaves infrastructure)

### GitHub Permissions
- Minimal permissions principle
- Read-only where possible
- Write only for PR reviews