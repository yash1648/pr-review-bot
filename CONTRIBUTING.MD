# Contributing to PR Review Bot

We appreciate your interest in contributing to PR Review Bot! This document provides guidelines and instructions for contributing.

## Code of Conduct

Be respectful, inclusive, and professional in all interactions.

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Ollama installed
- Git

### Development Setup

```bash
# Clone the repository
git clone https://github.com/yourusername/pr-review-bot.git
cd pr-review-bot

# Install dependencies
mvn clean install

# Start Ollama
ollama serve

# In another terminal, run the app
./mvnw spring-boot:run -Dspring-boot.run.arguments="--github.app-id=test --github.webhook-secret=test"
```

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

Branch naming conventions:
- `feature/` - New features
- `bugfix/` - Bug fixes
- `docs/` - Documentation updates
- `refactor/` - Code refactoring

### 2. Make Changes

Follow these code style guidelines:

**Java Code Style**:
```java
// Use meaningful variable names
String pullRequestTitle = prContext.getTitle();

// Prefer streams for collections
findings.stream()
    .filter(f -> "CRITICAL".equals(f.getSeverity()))
    .collect(Collectors.toList());

// Use Lombok for boilerplate
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyClass {
    private String field;
}

// Document complex logic
/**
 * Validates GitHub webhook signature using HMAC-SHA256
 * @param payload The webhook payload
 * @param signature The signature from GitHub headers
 * @return true if signature is valid, false otherwise
 */
public boolean verifySignature(String payload, String signature) {
    // Implementation
}
```

**Logging**:
```java
@Slf4j
@Service
public class MyService {
    public void doSomething() {
        log.info("Starting operation");
        // ...
        log.debug("Operation details: {}", details);
        // ...
        log.error("Error occurred", exception);
    }
}
```

### 3. Write Tests

Create tests for new features:

```java
@SpringBootTest
public class MyServiceTest {
    
    @InjectMocks
    private MyService service;
    
    @Mock
    private DependencyService dependency;
    
    @Test
    public void testFeature() {
        // Arrange
        when(dependency.getData()).thenReturn("test");
        
        // Act
        Result result = service.doSomething();
        
        // Assert
        assertEquals("expected", result.getValue());
        verify(dependency).getData();
    }
}
```

Run tests:
```bash
mvn test

# Run specific test
mvn test -Dtest=MyServiceTest
```

### 4. Add Documentation

Update relevant documentation:
- README.md - for user-facing changes
- DEPLOYMENT.md - for deployment changes
- Code comments - for complex logic

```markdown
## New Feature Title

Brief description of the feature.

### Usage

```java
code example
```

### Configuration

| Variable | Description |
|----------|-------------|
| VAR | Description |
```

### 5. Commit Changes

Write clear, descriptive commit messages:

```bash
git add .
git commit -m "feat: add support for custom rules

- Implement Rule interface for extensibility
- Add example CustomRule implementation
- Update documentation with examples

Closes #123"
```

Commit message format:
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `style:` - Code style/formatting
- `refactor:` - Code refactoring
- `perf:` - Performance improvement
- `test:` - Test additions

### 6. Push and Create Pull Request

```bash
git push origin feature/your-feature-name
```

Create a PR on GitHub with:
- Clear title describing the change
- Description of what changed and why
- Reference to related issues
- Screenshot/logs if applicable

**PR Template**:
```markdown
## Description
Brief description of changes

## Related Issue
Closes #123

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## How Has This Been Tested?
Describe testing done

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] Tests added/updated
- [ ] All tests pass
```

## Adding New Rules

### Step 1: Create Rule Class

```java
package com.prbot.analysis.heuristics;

import com.prbot.analysis.Rule;
import com.prbot.domain.ChangeChunk;
import com.prbot.domain.Finding;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MySampleRule implements Rule {
    
    @Override
    public List<Finding> analyze(ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();
        
        // Your analysis logic
        for (String line : chunk.getAddedLines()) {
            if (shouldFlag(line)) {
                findings.add(buildFinding(line, chunk));
            }
        }
        
        return findings;
    }
    
    @Override
    public String getName() {
        return "MySampleRule";
    }
    
    @Override
    public int getPriority() {
        return 500;
    }
    
    private boolean shouldFlag(String line) {
        // Your logic
        return false;
    }
    
    private Finding buildFinding(String line, ChangeChunk chunk) {
        return Finding.builder()
            .id(UUID.randomUUID().toString())
            .filePath(chunk.getFilePath())
            .lineNumber(chunk.getStartLine())
            .severity("MEDIUM")
            .category("MY_CATEGORY")
            .message("Description of issue")
            .suggestion("How to fix it")
            .source("HEURISTIC")
            .confidence(0.85)
            .precedenceScore(500)
            .build();
    }
}
```

### Step 2: Write Tests

```java
@SpringBootTest
public class MySampleRuleTest {
    
    @Autowired
    private MySampleRule rule;
    
    @Test
    public void testDetectsIssue() {
        ChangeChunk chunk = ChangeChunk.builder()
            .filePath("Test.java")
            .addedLines(List.of("problematic code"))
            .startLine(10)
            .build();
        
        List<Finding> findings = rule.analyze(chunk);
        
        assertEquals(1, findings.size());
        assertEquals("MEDIUM", findings.get(0).getSeverity());
    }
    
    @Test
    public void testIgnoresValidCode() {
        ChangeChunk chunk = ChangeChunk.builder()
            .filePath("Test.java")
            .addedLines(List.of("valid code"))
            .startLine(10)
            .build();
        
        List<Finding> findings = rule.analyze(chunk);
        
        assertTrue(findings.isEmpty());
    }
}
```

### Step 3: Add to Documentation

Update README with rule description and example.

## Pull Request Review Process

1. **Code Review**: Maintainers review for:
   - Code quality and style
   - Test coverage
   - Documentation completeness
   - Performance implications

2. **Testing**: Automated tests must pass:
   ```bash
   mvn clean verify
   ```

3. **CI/CD**: GitHub Actions runs tests and checks

4. **Approval**: Requires at least 2 approvals

5. **Merge**: Squash and merge to main

## Release Process

1. Update version in `pom.xml`
2. Update CHANGELOG.md
3. Create release branch
4. Create GitHub Release
5. Maven publishes to repository

## Reporting Issues

Use GitHub Issues with template:

```markdown
## Bug Report / Feature Request

### Description
Clear description of the issue

### Steps to Reproduce (for bugs)
1. Step 1
2. Step 2

### Expected Behavior
What should happen

### Actual Behavior
What actually happened

### Environment
- OS: 
- Java Version:
- Maven Version:
- Ollama Version:

### Logs
Include relevant logs
```

## Questions?

- Open a discussion on GitHub
- Check existing issues and documentation
- Reach out to maintainers

## License

By contributing, you agree your code will be licensed under MIT License.

Thank you for contributing to PR Review Bot!
