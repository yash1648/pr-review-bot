# Deployment Guide

This guide covers deploying PR Review Bot to production environments.

## Prerequisites

- Java 21 runtime
- Ollama service access (local or remote)
- GitHub App configured
- Docker (for containerized deployment)
- Kubernetes cluster (for K8s deployment)

## Local Development Deployment

### Using Maven

```bash
# Build the project
mvn clean package

# Run the application
java -jar target/pr-review-bot-1.0.0.jar \
  --github.app-id=YOUR_APP_ID \
  --github.webhook-secret=YOUR_SECRET \
  --llm.base-url=http://localhost:11434
```

### Using Docker

```bash
# Build Docker image
docker build -t pr-review-bot:latest .

# Run container
docker run -d \
  -p 8080:8080 \
  -e GITHUB_APP_ID=YOUR_APP_ID \
  -e GITHUB_WEBHOOK_SECRET=YOUR_SECRET \
  -e LLM_BASE_URL=http://host.docker.internal:11434 \
  -v $(pwd)/certs:/app/certs \
  --name pr-review-bot \
  pr-review-bot:latest
```

### Using Docker Compose

```bash
# Copy your GitHub private key
cp ~/Downloads/pr-review-bot.pem certs/github-app.pem

# Create .env file
cat > .env << EOF
GITHUB_APP_ID=YOUR_APP_ID
GITHUB_CLIENT_ID=YOUR_CLIENT_ID
GITHUB_WEBHOOK_SECRET=YOUR_SECRET
EOF

# Start services
docker-compose up -d

# View logs
docker-compose logs -f
```

## Production Deployment

### Server Requirements

**Minimum**:
- 2 CPU cores
- 2GB RAM
- 10GB disk space

**Recommended**:
- 4 CPU cores
- 4GB RAM
- 20GB disk space

### Environment Configuration

Create production `application.yml`:

```yaml
spring:
  application:
    name: pr-review-bot

server:
  port: 8080
  compression:
    enabled: true
    min-response-size: 1024

logging:
  level:
    root: INFO
    com.prbot: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

github:
  app-id: ${GITHUB_APP_ID}
  client-id: ${GITHUB_CLIENT_ID}
  webhook-secret: ${GITHUB_WEBHOOK_SECRET}
  private-key-path: /etc/secrets/github-app.pem

llm:
  model: qwen2.5-coder:7b
  base-url: http://ollama-service:11434
  timeout-seconds: 60
  enabled: true

app:
  max-diff-size-bytes: 1048576
  max-files-per-pr: 50
  heuristics-enabled: true
  llm-enabled: true
```

### Linux Systemd Service

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
ExecStart=/usr/bin/java -Xmx2g -jar pr-review-bot-1.0.0.jar
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

### Nginx Reverse Proxy

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
        
        # Webhook can take time to process
        proxy_read_timeout 30s;
        proxy_connect_timeout 10s;
    }
}
```

## Kubernetes Deployment

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pr-review-bot-config
  namespace: pr-review
data:
  application.yml: |
    spring:
      application:
        name: pr-review-bot
    logging:
      level:
        root: INFO
        com.prbot: INFO
    app:
      max-diff-size-bytes: 1048576
      max-files-per-pr: 50
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
            path: /webhook/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /webhook/health
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

## Monitoring

### Logs

```bash
# Docker
docker logs -f pr-review-bot

# Kubernetes
kubectl logs -f deployment/pr-review-bot -n pr-review

# Systemd
journalctl -u pr-review-bot -f
```

### Health Check

```bash
curl http://localhost:8080/webhook/health
# Response: OK
```

### Metrics (Future)

Consider adding Micrometer for Prometheus metrics:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

## Performance Tuning

### JVM Settings

```bash
java -Xms1g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+PrintGCDetails \
     -jar pr-review-bot-1.0.0.jar
```

### Connection Pooling

In `application.yml`:
```yaml
spring:
  webflux:
    # Configure connection pool size
    max-connections: 50
```

### LLM Optimization

- Use model caching for Ollama
- Configure appropriate timeouts
- Implement request queuing for high load

## Backup and Recovery

### Database Backups

Currently, PR Review Bot is stateless. If you add persistent storage:

```bash
# Regular backup of configuration and secrets
tar -czf backup-$(date +%Y%m%d).tar.gz certs/ .env
```

## Rollback

```bash
# Docker
docker pull pr-review-bot:previous-version
docker-compose down
docker-compose up -d

# Kubernetes
kubectl rollout undo deployment/pr-review-bot -n pr-review
```

## Support

For deployment issues:
1. Check application logs
2. Verify all environment variables are set
3. Ensure network connectivity to GitHub and Ollama
4. Review GitHub App permissions
