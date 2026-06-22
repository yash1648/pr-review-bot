# Deployment Guide

## Prerequisites

- Java 21 (JDK for builds, JRE for runtime)
- Docker
- A GitHub App with `private-key.pem` installed
- Ollama instance running the review model (`qwen2.5-coder:7b` recommended)

## Configuration

The bot supports two configuration layers:

1. **Environment variables** -- see [Production Considerations](#production-considerations)
2. **Repository-level YAML** -- place a `.prreview.yaml` file in the target repository to override settings per-repo

```yaml
# .prreview.yaml example
heuristics-enabled: true
llm-enabled: true
auto-approve: false
inline-comments: true
review-summary-enabled: true
```

## Docker Deployment

### Build Image

```bash
cd bot
./mvnw clean package -DskipTests
docker build -t pr-review-bot:latest .
```

### Dockerfile

The project uses a multi-stage build for a smaller final image.

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY src src/

RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built application
COPY --from=builder /build/target/bot-0.0.1-SNAPSHOT.jar app.jar

# Create certs directory for GitHub private key
RUN mkdir -p certs && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-jar", "app.jar"]
```

### Run Container

```bash
docker run -d \
  --name pr-review-bot \
  -p 8080:8080 \
  -e GITHUB_APP_ID=123456 \
  -e GITHUB_WEBHOOK_SECRET=your-webhook-secret \
  -v $(pwd)/certs:/app/certs:ro \
  --link ollama:ollama \
  pr-review-bot:latest
```

Mount your `certs/` directory containing `private-key.pem` into `/app/certs`.

## Docker Compose

The compose file lives at `bot/docker-compose.yml` and bundles Ollama alongside the bot.

```yaml
version: '3.8'

services:
  ollama:
    image: ollama/ollama:latest
    container_name: pr-review-ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    environment:
      - OLLAMA_HOST=0.0.0.0:11434
    command: serve

  pr-review-bot:
    build: .
    container_name: pr-review-bot
    ports:
      - "8080:8080"
    depends_on:
      - ollama
    environment:
      GITHUB_APP_ID: ${GITHUB_APP_ID}
      GITHUB_WEBHOOK_SECRET: ${GITHUB_WEBHOOK_SECRET}
      GITHUB_PRIVATE_KEY_PATH: /app/certs/private-key.pem
      LLM_BASE_URL: http://ollama:11434
      LLM_MODEL: qwen2.5-coder:7b
    volumes:
      - ./certs:/app/certs
    restart: unless-stopped

volumes:
  ollama_data:
```

Set environment variables in a `.env` file in the `bot/` directory:

```
GITHUB_APP_ID=12345
GITHUB_WEBHOOK_SECRET=your-webhook-secret
```

## Kubernetes Deployment

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pr-review-bot-config
  namespace: default
data:
  application.yaml: |
    app:
      heuristics-enabled: true
      llm-enabled: true
      auto-approve: false
      inline-comments: true
      review-summary-enabled: true
    github:
      webhook-secret: "${GITHUB_WEBHOOK_SECRET}"
    llm:
      model: qwen2.5-coder:7b
      base-url: http://ollama-service:11434
      timeout-seconds: 60
```

### Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pr-review-bot-secrets
  namespace: default
type: Opaque
stringData:
  github-webhook-secret: ${GITHUB_WEBHOOK_SECRET}
  private-key.pem: |
    -----BEGIN RSA PRIVATE KEY-----
    ... (your private key content)
    -----END RSA PRIVATE KEY-----
```

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pr-review-bot
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: pr-review-bot
  template:
    metadata:
      labels:
        app: pr-review-bot
    spec:
      containers:
      - name: pr-review-bot
        image: pr-review-bot:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: GITHUB_APP_ID
          valueFrom:
            secretKeyRef:
              name: pr-review-bot-secrets
              key: github-app-id
        - name: GITHUB_WEBHOOK_SECRET
          valueFrom:
            secretKeyRef:
              name: pr-review-bot-secrets
              key: github-webhook-secret
        - name: LLM_BASE_URL
          value: "http://ollama-service:11434"
        volumeMounts:
        - name: github-cert
          mountPath: /app/certs
          readOnly: true
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
      volumes:
      - name: github-cert
        secret:
          secretName: pr-review-bot-secrets
          items:
          - key: private-key.pem
            path: private-key.pem
```

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: pr-review-bot-service
  namespace: default
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
  selector:
    app: pr-review-bot
```

### Ollama Service (Optional)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: ollama-service
  namespace: default
spec:
  clusterIP: None
  ports:
  - port: 11434
    targetPort: 11434
  selector:
    app: ollama
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ollama
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ollama
  template:
    metadata:
      labels:
        app: ollama
    spec:
      containers:
      - name: ollama
        image: ollama/ollama:latest
        ports:
        - containerPort: 11434
        volumeMounts:
        - name: ollama-data
          mountPath: /root/.ollama
        resources:
          requests:
            memory: "4Gi"
            cpu: "1"
          limits:
            memory: "8Gi"
            cpu: "2"
      volumes:
      - name: ollama-data
        emptyDir: {}
```

## Production Considerations

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GITHUB_APP_ID` | Yes | GitHub App ID |
| `GITHUB_WEBHOOK_SECRET` | Yes | Strong random secret (32+ characters) |
| `GITHUB_PRIVATE_KEY_PATH` | No | Path to private key (default: `certs/private-key.pem`) |
| `LLM_PROVIDER` | No | LLM provider (default: `ollama`). Set to `nvidia-nim` for NVIDIA NIM |
| `LLM_BASE_URL` | No | Provider API base URL (default: `http://localhost:11434`) |
| `LLM_MODEL` | No | Model name (default: `qwen2.5-coder:7b`) |
| `LLM_TIMEOUT_SECONDS` | No | LLM request timeout (default: `60`) |
| `LLM_ENABLED` | No | Enable LLM reviews (default: `true`) |
| `LLM_API_KEY` | No | API key for NVIDIA NIM (`nvapi-...`). Required for hosted API |
| `HEURISTICS_ENABLED` | No | Enable heuristic checks (default: `true`) |
| `AUTO_APPROVE` | No | Auto-approve PRs passing review (default: `false`) |
| `INLINE_COMMENTS` | No | Post inline review comments (default: `true`) |
| `REVIEW_SUMMARY_ENABLED` | No | Post summary comment (default: `true`) |

### Security Best Practices

1. Use strong webhook secrets (minimum 32 characters)
2. Rotate GitHub App private keys regularly
3. Use TLS for all network communication
4. Store secrets in a secrets management system (Vault, AWS Secrets Manager)
5. Run container as non-root user (enabled by default in the Dockerfile)
6. Set resource limits
7. Enable audit logging

### Performance Tuning

1. **LLM Timeout**: Adjust based on model complexity
   ```properties
   llm.timeout-seconds=60
   ```

2. **Thread Pool**: Configure async processing
   ```properties
   spring.task.execution.pool.core-size=5
   spring.task.execution.pool.max-size=10
   ```

3. **JVM Heap**: Controlled via Dockerfile flags
   ```dockerfile
   -XX:MaxRAMPercentage=75.0
   -XX:InitialRAMPercentage=50.0
   ```

### Monitoring and Logging

Enable Spring Boot Actuator endpoints:

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.show-details=always
```

Set up logging aggregation:

```properties
logging.level.com.bot.bot=INFO
logging.level.org.springframework.web=WARN
```

## Rollback Procedure

### Docker

```bash
# View image history
docker image history pr-review-bot:latest

# Rollback to previous version
docker run -d --name pr-review-bot pr-review-bot:v1.0.0
```

### Kubernetes

```bash
# Check rollout history
kubectl rollout history deployment/pr-review-bot

# Rollback to previous version
kubectl rollout undo deployment/pr-review-bot

# Rollback to specific revision
kubectl rollout undo deployment/pr-review-bot --to-revision=2
```

## Troubleshooting Deployment

### Check Logs

```bash
# Docker
docker logs pr-review-bot

# Kubernetes
kubectl logs -f deployment/pr-review-bot
```

### Verify Connectivity

```bash
# Test webhook endpoint
curl -X POST http://localhost:8080/webhook/github \
  -H "Content-Type: application/json" \
  -d '{"action": "opened"}'

# Check LLM connectivity
curl http://ollama-service:11434/api/tags
```

### Health Checks

```bash
# Spring Boot health endpoint
curl http://localhost:8080/actuator/health
```
