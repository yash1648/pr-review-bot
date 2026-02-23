package com.bot.bot.analysis;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeuristicsAnalysisEngine {
    private final List<Rule> rules;

    public List<Finding> analyze(List<ChangeChunk> chunks) {
        log.debug("Starting heuristics analysis on {} chunks", chunks.size());

        return chunks.parallelStream()
                .flatMap(chunk -> analyzeChunk(chunk).stream())
                .collect(Collectors.toList());
    }

    private List<Finding> analyzeChunk(ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();

        for (Rule rule : rules) {
            try {
                List<Finding> ruleFindings = rule.analyze(chunk);
                findings.addAll(ruleFindings);
                if (!ruleFindings.isEmpty()) {
                    log.debug("Rule {} found {} findings in {}", rule.getName(), ruleFindings.size(), chunk.getFilePath());
                }
            } catch (Exception e) {
                log.warn("Error executing rule {}", rule.getName(), e);
            }
        }

        return findings;
    }
}
