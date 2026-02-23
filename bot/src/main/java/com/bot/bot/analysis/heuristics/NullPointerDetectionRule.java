package com.bot.bot.analysis.heuristics;

import com.bot.bot.analysis.Rule;
import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class NullPointerDetectionRule implements Rule {
    private static final Pattern NULL_DEREFERENCE = Pattern.compile(
            "(?i)(?:var|let|const)?\\s+\\w+\\s*=\\s*[\\w.]+\\[\\w+\\]|[\\w.]+\\.\\w+\\s*\\.[\\w.]+\\s*[^\\?]"
    );

    @Override
    public List<Finding> analyze(ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();

        for (int i = 0; i < chunk.getAddedLines().size(); i++) {
            String line = chunk.getAddedLines().get(i);

            // Detect potential null pointer dereferences
            if (line.contains("null") || (!line.contains("?.") && line.contains("."))) {
                if (line.matches(".*[\\w\\]\\)]\\s*\\.\\s*\\w+.*") && !line.contains("try") && !line.contains("if")) {
                    findings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .filePath(chunk.getFilePath())
                            .lineNumber(chunk.getStartLine() + i)
                            .severity("HIGH")
                            .category("POTENTIAL_BUG")
                            .message("Potential null pointer dereference without null check")
                            .suggestion("Add null checks or use optional/safe navigation operators")
                            .source("HEURISTIC")
                            .confidence(0.70)
                            .precedenceScore(500)
                            .build());
                }
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