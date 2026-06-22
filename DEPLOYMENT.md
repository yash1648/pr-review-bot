# Deployment Guide

Production deployment guide for the GitHub PR Review Bot.

## Prerequisites

- Java 21 runtime
- Ollama service (local or remote)
- GitHub App [configured and installed](https://docs.github.com/en/apps)
- Docker (for containerized deployment)
- Kubernetes cluster (for K8s deployment)

## Build

```bash
cd bot/

# Build (skips tests for speed; run `./mvnw clean test` separately)
./mvnw clean package -DskipTests

# The fat JAR lands at:
#   bot/target/bot-0.0.1-SNAPSHOT.jar
```

## Local (JAR)

```bash
cd bot/

java -jar target/bot-0.0.1-SNAPSHOT.jar \
  --github.app-id=YOUR_APP_ID \
  --github.webhook-secret=YOUR_SECRET \
  --llm.base-url=http://localhost:11434
```

## Docker

```bash
# Build image from bot/
cd bot/
docker build -t pr-review-bot:latest .

# Run
docker run -d \
  -p 8080:8080 \
  -e GITHUB_APP_ID=YOUR_APP_ID \
  -e GITHUB_WEBHOOK_SECRET=YOUR_SECRET \
  -e LLM_BASE_URL=http://host.docker.internal:11434 \
  -v $(pwd)/certs:/app/certs \
  --name pr-review-bot \
  pr-review-bot:latest
```

## Docker Compose (Ollama + Bot)

The compose file at `bot/docker-compose.yml` bundles Ollama and the bot together.

```bash
cd bot/

# Place your GitHub App private key
cp ~/Downloads/pr-review-bot.pem certs/github-app.pem

# Create .env (or copy .env.example)
cat > .env << EOF
GITHUB_APP_ID=YOUR_APP_ID
GITHUB_CLIENT_ID=YOUR_CLIENT_ID
GITHUB_WEBHOOK_SECRET=YOUR_SECRET
GITHUB_PRIVATE_KEY_PATH=certs/github-app.pem
LLM_MODEL=qwen2.5-coder:7b
LLM_BASE_URL=http://localhost:11434
EOF

# Start everything
docker compose up -d

# Tail logs
docker compose logs -f
```

## Server Requirements

| Tier   | CPU | RAM  | Disk |
|--------|-----|------|------|
| Min    | 2   | 2 GB | 10 GB|
| Rec.   | 4   | 4 GB | 20 GB|

## Environment Variables

The app reads these from the environment or a `.env` file:

| Variable                   | Required | Default                  | Description                        |
|----------------------------|----------|--------------------------|------------------------------------|
| `GITHUB_APP_ID`            | yes      | —                        | GitHub App ID                      |
| `GITHUB_CLIENT_ID`         | yes      | —                        | GitHub App client ID               |
| `GITHUB_WEBHOOK_SECRET`    | yes      | —                        | Webhook secret token               |
| `GITHUB_PRIVATE_KEY_PATH`  | yes      | `certs/private-key.pem`  | Path to the App's private key      |
| `LLM_BASE_URL`             | yes      | `http://localhost:11434`  | Ollama API base URL                |
| `LLM_MODEL`                | no       | `qwen2.5-coder:7b`       | Ollama model name                  |
| `LLM_TIMEOUT_SECONDS`      | no       | `60`                     | LLM request timeout                |
| `LLM_ENABLED`              | no       | `true`                   | Set to `false` to disable LLM      |
| `HEURISTICS_ENABLED`       | no       | `true`                   | Enable heuristic (regex) analysis  |
| `AUTO_APPROVE`             | no       | `false`                  | Auto-approve PRs passing review    |
| `INLINE_COMMENTS`          | no       | `true`                   | Post inline review comments        |
| `REVIEW_SUMMARY_ENABLED`   | no       | `true`                   | Post a summary comment             |

> **Note:** `GITHUB_CLIENT_SECRET` and `GITHUB_REDIRECT_URI` are **not** used. Only the fields above are required by the application.

### application.yaml Reference

All properties are configurable via env vars. The canonical source is `bot/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: bot

server:
  port: 8080

logging:
  level:
    root: INFO
    com.bot.bot: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,info

github:
  app-id: ${GITHUB_APP_ID}
  client-id: ${GITHUB_CLIENT_ID}
  webhook-secret: ${GITHUB_WEBHOOK_SECRET}
  private-key-path: ${GITHUB_PRIVATE_KEY_PATH}
  api-url: https://api.github.com

llm:
  model: ${LLM_MODEL:qwen2.5-coder:7b}
  base-url: ${LLM_BASE_URL:http://localhost:11434}
  timeout-seconds: ${LLM_TIMEOUT_SECONDS:60}
  enabled: ${LLM_ENABLED:true}

app:
  heuristics-enabled: ${HEURISTICS_ENABLED:true}
  llm-enabled: ${LLM_ENABLED:true}
  auto-approve: ${AUTO_APPROVE:false}
  inline-comments: ${INLINE_COMMENTS:true}
  review-summary-enabled: ${REVIEW_SUMMARY_ENABLED:true}
```

## Systemd Service

Create `/etc/systemd/system/pr-review-bot.service`:

```ini
[Unit]
Description=PR Review Bot
After=network.target

[Service]
Type=simple
User=prbot
WorkingDirectory=/opt/pr-review-bot
EnvironmentFile=/opt/pr-review-bot/.env
ExecStart=/usr/bin/java -Xmx2g -jar /opt/pr-review-bot/bot-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl enable pr-review-bot
sudo systemctl start pr-review-bot
```

## Nginx Reverse Proxy

```nginx
upstream pr_review_bot {
    server localhost:8080;
}

server {
    listen 443 ssl;
    server_name pr-review-bot.example.com;

    ssl_certificate /etc/ssl/certs/cert.pem;
    ssl_certificate_key /etc/ssl/private/key.pem;

    location / {
        proxy_pass http://pr_review_bot;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Webhook processing can take time
        proxy_read_timeout 30s;
        proxy_connect_timeout 10s;
    }
}
```

## Kubernetes Deployment

All manifests assume the `pr-review` namespace.

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pr-review-bot-config
  namespace: pr-review
data:
  application.yaml: |
    spring:
      application:
        name: bot
    logging:
      level:
        root: INFO
        com.bot.bot: DEBUG
    app:
      heuristics-enabled: true
      llm-enabled: true
      auto-approve: false
      inline-comments: true
      review-summary-enabled: true
```

### Secret

```bash
kubectl create secret generic pr-review-bot-secrets \
  --from-literal=github-app-id=YOUR_APP_ID \
  --from-literal=github-webhook-secret=YOUR_SECRET \
  --from-file=github-app-pem=certs/github-app.pem \
  -n pr-review
```

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pr-review-bot
  namespace: pr-review
spec:
  replicas: 2
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
        - name: GITHUB_PRIVATE_KEY_PATH
          value: /etc/secrets/github-app.pem
        - name: LLM_BASE_URL
          value: http://ollama:11434
        volumeMounts:
        - name: secrets
          mountPath: /etc/secrets
          readOnly: true
        - name: config
          mountPath: /app/config
          readOnly: true
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
      volumes:
      - name: secrets
        secret:
          secretName: pr-review-bot-secrets
      - name: config
        configMap:
          name: pr-review-bot-config
```

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: pr-review-bot
  namespace: pr-review
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
  selector:
    app: pr-review-bot
```

### Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: pr-review-bot
  namespace: pr-review
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - pr-review-bot.example.com
    secretName: pr-review-bot-tls
  rules:
  - host: pr-review-bot.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: pr-review-bot
            port:
              number: 80
```

Deploy:

```bash
kubectl apply -f k8s/
```

## Health Checks

The bot exposes two health endpoints:

| Endpoint              | Purpose                          |
|-----------------------|----------------------------------|
| `/webhook/health`     | Application-level liveness       |
| `/actuator/health`    | Spring Boot actuator health      |

```bash
curl http://localhost:8080/webhook/health
# => OK

curl http://localhost:8080/actuator/health
# => {"status":"UP"}
```

## Logs

```bash
# Docker / Compose
docker logs -f pr-review-bot

# Kubernetes
kubectl logs -f deployment/pr-review-bot -n pr-review

# Systemd
journalctl -u pr-review-bot -f
```

## JVM Tuning

```bash
java -Xms1g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar bot-0.0.1-SNAPSHOT.jar
```

## Backup & Rollback

The bot is **stateless** — no database to back up. Protect configuration and secrets:

```bash
tar -czf backup-$(date +%Y%m%d).tar.gz bot/certs/ bot/.env
```

Rollback:

```bash
# Docker
docker pull pr-review-bot:previous-version
docker compose down
docker compose up -d

# Kubernetes
kubectl rollout undo deployment/pr-review-bot -n pr-review
```

## Troubleshooting

1. **Check logs** — start with `journalctl`, `docker logs`, or `kubectl logs`.
2. **Verify env vars** — ensure all required variables from the table above are set.
3. **Network** — confirm the bot can reach both `api.github.com` and the Ollama server.
4. **GitHub App** — double‑check the App permissions, webhook URL, and private key.
5. **Ollama** — run `curl http://<ollama-host>:11434/api/tags` to verify it's responding.
