# Deployment Guide

## Docker Deployment

### Build Image

```bash
./mvnw clean package -DskipTests
docker build -t pr-review-bot:latest .
```

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine

# Install dependencies
RUN apk add --no-cache curl

# Create app directory
WORKDIR /app

# Copy JAR
COPY target/pr-review-bot-*.jar app.jar

# Copy certificates
COPY certs/github-app.pem /app/certs/github-app.pem

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Run Container

```bash
docker run -d \
  --name pr-review-bot \
  -p 8080:8080 \
  -e GITHUB_APP_ID=123456 \
  -e GITHUB_CLIENT_ID=Iv1.xxx \
  -e GITHUB_WEBHOOK_SECRET=secret \
  -v $(pwd)/certs:/app/certs:ro \
  --link ollama:ollama \
  pr-review-bot:latest
```

## Docker Compose

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - GITHUB_APP_ID=${GITHUB_APP_ID}
      - GITHUB_CLIENT_ID=${GITHUB_CLIENT_ID}
      - GITHUB_WEBHOOK_SECRET=${GITHUB_WEBHOOK_SECRET}
      - LLM_BASE_URL=http://ollama:11434
    volumes:
      - ./certs:/app/certs:ro
      - ./logs:/app/logs
    depends_on:
      - ollama
    networks:
      - pr-review-network

  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama-data:/root/.ollama
    ports:
      - "11434:11434"
    networks:
      - pr-review-network

volumes:
  ollama-data:

networks:
  pr-review-network:
    driver: bridge
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
  application.properties: |
    github.app.id=${GITHUB_APP_ID}
    github.client.id=${GITHUB_CLIENT_ID}
    llm.model=qwen2.5-coder
    llm.base-url=http://ollama-service:11434
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
  github-app.pem: |
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
        - name: GITHUB_CLIENT_ID
          valueFrom:
            secretKeyRef:
              name: pr-review-bot-secrets
              key: github-client-id
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
            path: /actuator/health/readiness
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
          - key: github-app.pem
            path: github-app.pem
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

Ensure the following are configured in production:

- `GITHUB_APP_ID`: Your GitHub App ID
- `GITHUB_CLIENT_ID`: Your GitHub App Client ID
- `GITHUB_WEBHOOK_SECRET`: Strong random secret (32+ characters)
- `LLM_BASE_URL`: URL to your Ollama instance
- `LLM_MODEL`: Model name (ensure it's pulled on the Ollama instance)
- `SERVER_SERVLET_CONTEXT_PATH`: Optional path prefix

### Security Best Practices

1. Use strong webhook secrets (minimum 32 characters)
2. Rotate GitHub App private keys regularly
3. Use TLS for all network communication
4. Store secrets in a secrets management system (Vault, AWS Secrets Manager)
5. Run container as non-root user
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

3. **Cache Settings**: Token caching for performance
   ```properties
   github.token.cache.ttl-minutes=55
   ```

### Monitoring & Logging

Enable Spring Boot Actuator endpoints:

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.show-details=always
```

Set up logging aggregation:

```properties
logging.level.com.pr_review_bot=INFO
logging.level.org.springframework.web=WARN
```

### Database Setup (Optional)

For persistence and analytics, set up PostgreSQL:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: database-credentials
stringData:
  username: bot_user
  password: secure_password
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: database-config
data:
  url: "jdbc:postgresql://postgres-service:5432/pr_review_bot"
```

Add to application properties:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
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