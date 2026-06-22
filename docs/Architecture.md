# Architecture Documentation

## Overview

PR Review Bot is a Spring Boot 4.0.2 application that automates code reviews for GitHub pull requests. It combines fast static analysis with LLM-based review to provide comprehensive feedback via the GitHub PR Review API.

## System Components

### 1. Webhook Layer

**Responsibility**: Receive and validate GitHub webhook events.

**Key Classes:**
- `GitHubWebhookController`: REST controller handling `POST /webhook/github` and `GET /webhook/health`
- `WebhookSignatureVerifier`: HMAC-SHA256 signature verification using webhook secret

**Behavior:**
- Verifies `X-Hub-Signature-256` header to prevent spoofing
- Filters non-`pull_request` events
- Processes only `opened`, `synchronize`, and `reopened` actions
- Returns `202 Accepted` immediately; processing is async via `@Async`

### 2. GitHub Integration

**Responsibility**: Authenticate and communicate with the GitHub API.

**Key Classes:**
- `GitHubJwtGenerator`: Creates and caches JWT tokens (RS256, 10-min expiry, 60s buffer). Supports PKCS#1 and PKCS#8 private key formats.
- `GitHubApiClient`: All GitHub API calls via WebClient — PR metadata, unified diffs (`application/vnd.github.v3.diff`), issue comments, and `submitReview()`.
- `ReviewPublisher`: Converts `Finding` results into `ReviewComment` models and invokes `GitHubApiClient.submitReview()` (`POST /repos/{owner}/{repo}/pulls/{number}/reviews`). Supports inline comments, summary body, and auto-approve (`APPROVE` event) when no issues found and `AUTO_APPROVE=true`.

**Authentication Flow:**
1. Generate JWT using the GitHub App private key (RS256, valid for 10 minutes)
2. Exchange JWT for an installation access token (not yet implemented; currently uses JWT directly)
3. Use the token for all API calls
4. JWT is cached until 60 seconds before expiry

**Key Details:**
- No third-party GitHub library — all calls use `WebClient` with raw REST endpoints
- `submitReview()` sends a single review event with inline comments + summary body, not individual issue comments

### 3. Diff Processing

**Responsibility**: Parse Git unified diff format into structured data.

**Key Classes:**
- `UnifiedDiffParser`: Regex-based parser for unified diff format
- `ChangeChunk`: Immutable record representing a diff hunk (file path, added/removed/context lines, line ranges)

**Parsing Strategy:**
- Extract file metadata (old/new paths, change type)
- Parse hunk headers (`@@ -start,count +start,count @@`)
- Separate added, removed, and context lines
- Binary files and non-Java files can be skipped (configurable)

### 4. Analysis Engine

#### 4.1 Heuristics Analysis

**Responsibility**: Fast, deterministic code analysis.

**Key Classes:**
- `HeuristicsAnalysisEngine`: Orchestrates all heuristic `Rule` implementations across diff chunks using parallel streams.
- `Rule`: Interface contract for all heuristic rules.
- `SecretsDetectionRule`: Detects hardcoded secrets via regex patterns (AWS keys, API tokens, private keys, passwords, GitHub tokens, Slack tokens).
- `NullPointerDetectionRule`: Identifies potential null pointer dereferences by detecting chained method calls without null checks.

**Characteristics:**
- Execution time: <10ms per chunk
- No external dependencies
- 100% deterministic
- Runs in parallel across chunks via `parallelStream()`

#### 4.2 LLM Review

**Responsibility**: Contextual, intelligent code analysis using local LLMs.

**Key Classes:**
- `LLMClient` (interface): Common contract for all LLM providers — `generateCodeReview(String) -> Mono<String>`.
- `OllamaClient`: Implements `LLMClient` for Ollama's `/api/generate` endpoint. Active when `LLM_PROVIDER=ollama` (default).
- `NvidiaNimClient`: Implements `LLMClient` for NVIDIA NIM's OpenAI-compatible `/v1/chat/completions` endpoint. Active when `LLM_PROVIDER=nvidia-nim`. Supports Bearer token auth via `LLM_API_KEY`.
- `LLMReviewEngine`: Constructs prompts with diff context and file metadata, invokes the active `LLMClient`, and parses structured text into `Finding` objects. No separate `ReviewPromptBuilder` class — prompt construction is internal to this engine.

**Prompt Strategy:**
- Include surrounding file context
- Provide specific diff hunk with line numbers
- Request structured JSON output (one finding per JSON object)
- Instruct to focus on bugs and security, ignore style

**Configuration:**
- Provider selection via `LLM_PROVIDER` env var (`ollama` or `nvidia-nim`)
- Model selection via `LLM_MODEL` env var
- Recommended models: `qwen2.5-coder:7b`, `deepseek-coder:6.7b`, `codellama:7b-code`
- NVIDIA NIM default base URL: `http://localhost:8000` (self-hosted) or `https://integrate.api.nvidia.com/v1` (hosted API)
- NVIDIA NIM requires `LLM_API_KEY` for the hosted API (starts with `nvapi-`)

### 5. Finding Merger

**Responsibility**: Combine and deduplicate findings from heuristics and LLM analysis.

**Key Classes:**
- `FindingMerger`: Implements `mergeAndRank()` algorithm.

**Algorithm:**
1. Group findings by file path + line number
2. For conflicts, keep the highest severity
3. Combine messages from different sources
4. Sort by severity (critical first), then by file/line

### 6. Per-Repo Configuration

**Responsibility**: Allow per-repository customization via `.prreview.yaml` in the default branch.

**Key Classes:**
- `ReviewConfig`: Data model — `enabled`, `autoApprove`, `inlineComments`, `reviewSummary`, `ignorePaths`, `ignoreRules`, `llmModel`.
- `RepoConfigLoader`: Fetches `.prreview.yaml` from `https://raw.githubusercontent.com/{owner}/{repo}/HEAD/.prreview.yaml` via `WebClient`. Returns empty config (defaults) if file is missing or unparseable.

**Priority (lowest to highest):** `application.yaml` defaults → environment variables → per-repo `.prreview.yaml`

### 7. Inline Comment System

**Responsibility**: Provide line-specific feedback in PR reviews.

**Key Classes:**
- `ReviewComment`: Model — `path`, `line`, `startLine` (multi-line), `side` (LEFT/RIGHT), `body`.
- `ReviewPublisher.buildInlineComments()`: Maps `Finding` objects with precise line numbers to `ReviewComment` models.
- `GitHubApiClient.submitReview()`: `POST /repos/{owner}/{repo}/pulls/{number}/reviews` with `event` (APPROVE/COMMENT/REQUEST_CHANGES), `body` (summary), `comments` (inline comment array).

### 8. Health and Monitoring

**Endpoints:**
- `/actuator/health` — Spring Boot Actuator health check
- `/actuator/info` — Application info from Actuator
- `/webhook/health` — Simple liveness check returning `200 OK`

## Data Flow

```
GitHub Webhook
    |
    v
GitHubWebhookController
    |  Verify X-Hub-Signature-256  |  Filter pull_request (opened/synchronize/reopened)  |  202 Accepted
    v (async)
ReviewOrchestrator.processPullRequest()
    |
    |-- GitHubApiClient.fetchPullRequestContext()   -- Parse webhook JSON
    |-- RepoConfigLoader.loadConfig()               -- Fetch .prreview.yaml (raw.githubusercontent.com)
    |-- GitHubApiClient.fetchDiff()                 -- GET /repos/*/*/pulls/N (Accept: v3.diff)
    |-- UnifiedDiffParser.parse()                   -- Split diff into ChangeChunk[]
    |
    |-- analyzeDiff()
    |   |-- Filter chunks by ignorePaths
    |   |-- HeuristicsAnalysisEngine.analyze()      -- parallel, synchronous
    |   |-- LLMReviewEngine.analyzeWithLLM()        -- async (Mono)
    |   |       |-- OllamaClient.generateCodeReview() -> POST /api/generate
    |
    |-- FindingMerger.mergeAndRank()                -- Dedup by file+line, keep highest severity, sort
    |-- ReviewPublisher.publishReview()
    |       |-- Build inline comments + summary body
    |       |-- Determine event: APPROVE / COMMENT
    |       |-- GitHubApiClient.submitReview()      -- POST /repos/*/*/pulls/N/reviews
    v
Review posted to GitHub
```

## Technology Choices

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Framework | Spring Boot 4.0.2 | Latest major version, virtual threads support |
| Language | Java 21 | Records, pattern matching, virtual threads, modern GC |
| HTTP Client | WebClient (Reactor) | Used for ALL external calls (GitHub API, Ollama, raw content) |
| GitHub API | Custom WebClient-based (`GitHubApiClient`) | No third-party library dependency; full control over API surface |
| LLM Runtime | Ollama | Local inference, OpenAI-compatible API, no data leaves infrastructure |
| Build Tool | Maven Wrapper (`./mvnw`) | Reproducible builds, no local Maven install needed |
| JSON | Gson | Lightweight, no annotation model required for webhook parsing |
| YAML | SnakeYAML | Parse `.prreview.yaml` per-repo config files |
| JWT | jjwt 0.12.3 | GitHub App authentication, RS256 signing |
| Auth | PKCS#1 + PKCS#8 private key support | Handles both GitHub-provided key formats |

## Domain Models

- `PullRequestContext`: PR metadata — owner, repo, PR number, title, description, author, refs, commit SHA.
- `ChangeChunk`: Parsed diff hunk — file path, added/removed/context lines, line ranges.
- `Finding`: Analysis result — severity, category, file path, line number, message, suggestion, source.
- `ReviewComment`: Inline comment — path, line, startLine, side (LEFT/RIGHT), body.
- `ReviewConfig`: Per-repo config — enabled, autoApprove, inlineComments, ignorePaths, ignoreRules, llmModel.

## Scalability Considerations

### Current Limitations
- Single instance (no horizontal scaling)
- In-memory JWT and private key cache
- Synchronous heuristics (parallelStream within process)

### Future Improvements
- Redis for distributed token cache
- Message queue (RabbitMQ/SQS) for webhook events
- Separate worker pool for LLM inference
- Database for review history and analytics

## Security Architecture

### Secrets Management
- GitHub App private key mounted as filesystem secret
- Environment variables for App ID, webhook secret, Ollama URL
- No secrets in code or logs

### Authentication
- Webhook signature verification: `X-Hub-Signature-256` (HMAC-SHA256)
- JWT-based GitHub App auth with RS256 signing
- Supports PKCS#1 and PKCS#8 private key formats
- Installation access token exchange (planned)

### Network Security
- Local-only LLM inference — no code data leaves infrastructure
- TLS for all outbound GitHub API calls
- Per-repo `.prreview.yaml` fetched over HTTPS from `raw.githubusercontent.com` (public, no auth required)

### GitHub Permissions
- Minimal permission principle
- Read-only: PR metadata, diffs
- Write: PR reviews only
