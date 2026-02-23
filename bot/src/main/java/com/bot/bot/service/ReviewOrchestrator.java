package com.bot.bot.service;


import com.google.gson.JsonObject;
import com.bot.bot.analysis.HeuristicsAnalysisEngine;
import com.bot.bot.analysis.LLMReviewEngine;
import com.bot.bot.config.AppProperties;
import com.bot.bot.diff.UnifiedDiffParser;
import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.engine.FindingMerger;
import com.bot.bot.engine.ReviewPublisher;
import com.bot.bot.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOrchestrator {
    private final GitHubApiClient gitHubApiClient;
    private final UnifiedDiffParser diffParser;
    private final HeuristicsAnalysisEngine heuristicsAnalysisEngine;
    private final LLMReviewEngine llmReviewEngine;
    private final FindingMerger findingMerger;
    private final ReviewPublisher reviewPublisher;
    private final AppProperties appProperties;

    /**
     * Process pull request asynchronously.
     * Fetches PR data, analyzes changes, and publishes review.
     */
    @Async
    public void processPullRequest(JsonObject webhookData) {
        log.info("Starting PR review process");

        try {
            // Chain the entire operation as a reactive pipeline
            gitHubApiClient.fetchPullRequestContext(webhookData)
                    .flatMap(prContext -> processPullRequestContext(prContext))
                    .block(); // Block is acceptable here since we're in @Async context

        } catch (Exception e) {
            log.error("Error processing PR review", e);
        }
    }

    /**
     * Process PR context: fetch diff, analyze, and publish review.
     */
    private Mono<Void> processPullRequestContext(PullRequestContext prContext) {
        log.info("Processing PR {}/{}/#{}", prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber());

        // Fetch diff and start analysis pipeline
        return gitHubApiClient.fetchDiff(prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber())
                .map(diff -> {
                    List<ChangeChunk> chunks = diffParser.parse(diff);
                    log.info("Parsed {} change chunks", chunks.size());
                    return chunks;
                })
                .flatMap(chunks -> analyzeDiff(prContext, chunks))
                .doOnError(e -> log.error("Error in PR processing", e));
    }

    /**
     * Analyze changes: run heuristics and LLM analysis, merge findings, and publish.
     */
    private Mono<Void> analyzeDiff(PullRequestContext prContext, List<ChangeChunk> chunks) {
        log.debug("Starting diff analysis");

        // Build list of findings
        List<Finding> findings = Collections.synchronizedList(new ArrayList<>());

        // 1. Run heuristics analysis (synchronous, fast)
        if (appProperties.isHeuristicsEnabled()) {
            log.debug("Running heuristics analysis");
            List<Finding> heuristicFindings = heuristicsAnalysisEngine.analyze(chunks);
            findings.addAll(heuristicFindings);
            log.info("Heuristics found {} findings", heuristicFindings.size());
        }

        // 2. Run LLM analysis (asynchronous, can be slow)
        if (appProperties.isLlmEnabled()) {
            log.debug("Running LLM analysis");

            return llmReviewEngine.analyzeWithLLM(prContext, chunks)
                    .flatMap(llmFindings -> {
                        if (llmFindings != null && !llmFindings.isEmpty()) {
                            findings.addAll(llmFindings);
                            log.info("LLM found {} findings", llmFindings.size());
                        }
                        return publishReviewWithFindings(prContext, findings);
                    })
                    .doOnError(e -> log.error("Error in LLM analysis", e));
        } else {
            // Skip LLM analysis, go straight to publishing
            return publishReviewWithFindings(prContext, findings);
        }
    }

    /**
     * Merge, rank, and publish review findings.
     */
    private Mono<Void> publishReviewWithFindings(PullRequestContext prContext, List<Finding> findings) {
        log.debug("Merging and ranking {} findings", findings.size());

        // 3. Merge and rank findings
        List<Finding> rankedFindings = findingMerger.mergeAndRank(findings);
        log.info("Final {} findings after deduplication and ranking", rankedFindings.size());

        // 4. Publish review
        log.debug("Publishing review to {}/{}/PR#{}", prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber());
        return reviewPublisher.publishReview(prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(), rankedFindings)
                .doOnSuccess(v -> log.info("Review published successfully for {}/{}/PR#{}",
                        prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber()))
                .doOnError(e -> log.error("Error publishing review for {}/{}/PR#{}",
                        prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(), e));
    }
}