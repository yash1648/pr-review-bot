package com.bot.bot.engine;

import com.bot.bot.domain.Finding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FindingMerger {

    public List<Finding> mergeAndRank(List<Finding> findings) {
        log.debug("Merging and ranking {} findings", findings.size());

        // Deduplicate similar findings
        Map<String, Finding> merged = new HashMap<>();

        for (Finding finding : findings) {
            String key = generateKey(finding);

            if (merged.containsKey(key)) {
                // Merge with existing finding - keep the one with higher confidence
                Finding existing = merged.get(key);
                if (finding.getConfidence() > existing.getConfidence()) {
                    merged.put(key, finding);
                }
            } else {
                merged.put(key, finding);
            }
        }

        // Sort by precedence and severity
        return merged.values().stream()
                .sorted(Comparator
                        .comparingInt((Finding f) -> -f.getPrecedenceScore()) // Higher score first
                        .thenComparing(f -> severityToInt(f.getSeverity()), Comparator.reverseOrder())
                        .thenComparingDouble(f -> -f.getConfidence()) // Higher confidence first
                )
                .collect(Collectors.toList());
    }

    private String generateKey(Finding finding) {
        return String.format("%s:%d:%s", finding.getFilePath(), finding.getLineNumber(), finding.getCategory());
    }

    private int severityToInt(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            case "INFO" -> 0;
            default -> -1;
        };
    }
}
