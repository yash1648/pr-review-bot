package com.bot.bot.analysis;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.llm.OllamaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewEngine {
    private final OllamaClient ollamaClient;

    /**
     * Analyze code chunks using LLM for contextual review.
     * Processes chunks in parallel and aggregates results.
     */
    public Mono<List<Finding>> analyzeWithLLM(PullRequestContext prContext, List<ChangeChunk> chunks) {
        log.debug("Starting LLM analysis on {} chunks", chunks.size());

        if (chunks.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }

        // Convert list to Flux, process in parallel, collect results
        return Flux.fromIterable(chunks)
                .flatMap(chunk -> generateReviewForChunk(chunk, prContext))
                .flatMap(Flux::fromIterable)  // Flatten the list of findings from each chunk
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Error in LLM analysis", e);
                    return Mono.just(new ArrayList<>());
                })
                .doOnSuccess(findings -> log.debug("LLM analysis completed with {} findings", findings.size()));
    }

    /**
     * Generate review for a single chunk and return list of findings.
     */
    private Mono<List<Finding>> generateReviewForChunk(ChangeChunk chunk, PullRequestContext prContext) {
        String prompt = buildPrompt(chunk, prContext);

        return ollamaClient.generateCodeReview(prompt)
                .map(response -> parseReviewResponse(response, chunk))
                .onErrorResume(e -> {
                    log.warn("Error generating review for chunk {}", chunk.getFilePath(), e);
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Build a comprehensive prompt for the LLM with context.
     */
    private String buildPrompt(ChangeChunk chunk, PullRequestContext prContext) {
        return String.format(
                """
                You are an expert code reviewer. Analyze the following code change and provide specific, actionable feedback.
                
                File: %s
                Change Type: %s
                
                Changed Code:
                %s
                
                Context:
                %s
                
                PR Title: %s
                PR Description: %s
                
                Provide your review in a structured format:
                1. Issues Found (if any): List each issue with severity (CRITICAL, HIGH, MEDIUM, LOW)
                2. Suggestions for Improvement
                3. Positive Observations (if any)
                
                Be concise and focus on substantive issues.
                """,
                chunk.getFilePath(),
                chunk.getChangeType(),
                String.join("\n", chunk.getAddedLines()),
                chunk.getContext(),
                prContext.getTitle(),
                prContext.getDescription() != null ? prContext.getDescription() : "No description"
        );
    }

    /**
     * Parse LLM response and extract findings.
     * Simple heuristic-based parsing - can be enhanced with NLP.
     */
    private List<Finding> parseReviewResponse(String response, ChangeChunk chunk) {
        List<Finding> findings = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return findings;
        }

        // Split response into lines and look for severity keywords
        String[] lines = response.split("\n");

        for (String line : lines) {
            String lowerLine = line.toLowerCase();

            // Detect critical issues
            if (lowerLine.contains("critical") || lowerLine.contains("danger")) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("CRITICAL")
                        .category("CODE_REVIEW")
                        .message(line.trim())
                        .source("LLM")
                        .confidence(0.85)
                        .precedenceScore(700)
                        .build());
            }
            // Detect high severity issues
            else if (lowerLine.contains("high") || lowerLine.contains("issue") || lowerLine.contains("bug")) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("HIGH")
                        .category("CODE_REVIEW")
                        .message(line.trim())
                        .source("LLM")
                        .confidence(0.80)
                        .precedenceScore(650)
                        .build());
            }
            // Detect medium severity issues
            else if (lowerLine.contains("medium") || lowerLine.contains("warning") || lowerLine.contains("improve")) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .filePath(chunk.getFilePath())
                        .lineNumber(chunk.getStartLine())
                        .severity("MEDIUM")
                        .category("CODE_REVIEW")
                        .message(line.trim())
                        .source("LLM")
                        .confidence(0.75)
                        .precedenceScore(600)
                        .build());
            }
        }

        return findings;
    }
}