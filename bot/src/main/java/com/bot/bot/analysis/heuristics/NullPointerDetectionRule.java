package com.bot.bot.analysis.heuristics;

import com.bot.bot.analysis.Rule;
import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class NullPointerDetectionRule implements Rule {

    // Match chained method calls like `obj.getField().getValue()` (potential NPE)
    private static final Pattern CHAINED_CALL = Pattern.compile(
            "[\\w)]]\\s*\\.\\s*\\w+\\s*\\(.*\\)\\s*\\.\\s*\\w+"
    );

    // Lines likely to be comments or type declarations (skip these)
    private static final Pattern COMMENT_OR_DECL = Pattern.compile(
            "^(\\s*[*//]|\\s*import|\\s*@|\\s*package|\\s*public|\\s*private|\\s*protected)"
    );

    // Explicit null check patterns (skip these)
    private static final Pattern NULL_CHECK = Pattern.compile(
            "null\\s*[=!]=\\s*\\w+\\s*[|&]{2}|\\w+\\s*[=!]=\\s*null|Objects\\.nonNull|Objects\\.requireNonNull"
    );

    @Override
    public List<Finding> analyze(ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();

        for (int i = 0; i < chunk.getAddedLines().size(); i++) {
            String line = chunk.getAddedLines().get(i);

            // Skip comments, imports, annotations, and declarations
            if (COMMENT_OR_DECL.matcher(line).find()) continue;

            // Skip lines with explicit null checks
            if (NULL_CHECK.matcher(line).find()) continue;

            // Check for chained method calls without safe navigation
            if (!line.contains("?.") && CHAINED_CALL.matcher(line).find()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine() + i)
                        .severity("MEDIUM")
                        .category("POTENTIAL_BUG")
                        .message("Chained method call without null-safe operator (?.): " + line.trim())
                        .suggestion("Consider using ?. for safe navigation or add explicit null checks before accessing nested properties")
                        .source("HEURISTIC")
                        .confidence(0.65)
                        .precedenceScore(500)
                        .build());
            }
        }

        return findings;
    }

    @Override
    public String getName() {
        return "NullPointerDetectionRule";
    }

    @Override
    public int getPriority() {
        return 500;
    }
}
