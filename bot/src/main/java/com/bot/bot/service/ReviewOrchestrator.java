package com.bot.bot.service;


import com.google.gson.JsonObject;
import com.bot.bot.analysis.HeuristicsAnalysisEngine;
import com.bot.bot.analysis.LLMReviewEngine;
import com.bot.bot.config.AppProperties;
import com.bot.bot.config.RepoConfigLoader;
import com.bot.bot.config.ReviewConfig;
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

import java.util.ArrayList;
import java.util.List;

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
    private final RepoConfigLoader repoConfigLoader;

    /**
     * Process pull request asynchronously.
     * Fetches PR data, loads per-repo config, analyzes changes, and publishes review.
     */
    @Async("reviewTaskExecutor")
    public void processPullRequest(JsonObject webhookData) {
        log.info("Starting PR review process");

        try {
            // Parse PR metadata (synchronous, from webhook JSON — no API call)
            PullRequestContext prContext = gitHubApiClient.fetchPullRequestContext(webhookData);

            // Load per-repo config
            loadRepoConfig(prContext)
                    .flatMap(config -> processPullRequestContext(prContext, config))
                    .block();
        } catch (Exception e) {
            log.error("Error processing PR review", e);
        }
    }

    /** Fetch per-repo config, falling back to defaults. */
    private Mono<ReviewConfig> loadRepoConfig(PullRequestContext prContext) {
        return repoConfigLoader.loadConfig(prContext.getOwner(), prContext.getRepo())
                .defaultIfEmpty(new ReviewConfig())
                .doOnNext(config -> {
                    if (!config.isEnabled()) {
                        log.info("PR review disabled by .prreview.yaml for {}/{}",
                                prContext.getOwner(), prContext.getRepo());
                    }
                });
    }

    /**
     * Process PR context: fetch diff, analyze, and publish review.
     */
    private Mono<Void> processPullRequestContext(PullRequestContext prContext, ReviewConfig config) {
        if (!config.isEnabled()) {
            return Mono.empty();
        }

        long installationId = prContext.getInstallationId();
        log.info("Processing PR {}/{}/#{} (installation {})",
                prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(), installationId);

        return gitHubApiClient.fetchDiff(prContext.getOwner(), prContext.getRepo(),
                        prContext.getPrNumber(), installationId)
                .map(diff -> {
                    List<ChangeChunk> chunks = diffParser.parse(diff);
                    log.info("Parsed {} change chunks", chunks.size());
                    return chunks;
                })
                .flatMap(chunks -> analyzeDiff(prContext, config, chunks))
                .doOnError(e -> log.error("Error in PR processing", e));
    }

    /**
     * Analyze changes: run heuristics and LLM analysis, merge findings, and publish.
     */
    private Mono<Void> analyzeDiff(PullRequestContext prContext, ReviewConfig config, List<ChangeChunk> chunks) {
        log.debug("Starting diff analysis");

        // Filter chunks based on ignored paths
        List<ChangeChunk> filteredChunks = filterChunks(chunks, config);

        // Build list of findings
        List<Finding> findings = new ArrayList<>();

        // 1. Run heuristics analysis (synchronous)
        if (appProperties.isHeuristicsEnabled()) {
            log.debug("Running heuristics analysis");
            List<Finding> heuristicFindings = heuristicsAnalysisEngine.analyze(filteredChunks);
            findings.addAll(heuristicFindings);
            log.info("Heuristics found {} findings", heuristicFindings.size());
        }

        // 2. Run LLM analysis (asynchronous)
        Mono<List<Finding>> llmResult = appProperties.isLlmEnabled()
                ? llmReviewEngine.analyzeWithLLM(prContext, filteredChunks)
                    .onErrorResume(e -> {
                        log.error("LLM analysis failed, continuing with heuristics only", e);
                        return Mono.just(new ArrayList<>());
                    })
                : Mono.just(new ArrayList<>());

        return llmResult.flatMap(llmFindings -> {
            if (llmFindings != null && !llmFindings.isEmpty()) {
                // Filter out findings matching ignored rules
                List<Finding> filtered = filterFindingsByConfig(llmFindings, config);
                findings.addAll(filtered);
                log.info("LLM found {} findings ({} after filtering)", llmFindings.size(), filtered.size());
            }
            return publishReviewWithFindings(prContext, config, findings);
        });
    }

    /**
     * Filter out chunks matching ignored path patterns.
     */
    private List<ChangeChunk> filterChunks(List<ChangeChunk> chunks, ReviewConfig config) {
        if (config.getIgnorePaths().isEmpty()) return chunks;

        List<ChangeChunk> filtered = new ArrayList<>();
        for (ChangeChunk chunk : chunks) {
            boolean ignored = false;
            for (String pattern : config.getIgnorePaths()) {
                String filePath = chunk.getFilePath();
                if (filePath != null && (filePath.equals(pattern)
                        || filePath.endsWith(pattern.replace("*", ""))
                        || filePath.matches(pattern))) {
                    ignored = true;
                    break;
                }
            }
            if (!ignored) {
                filtered.add(chunk);
            }
        }
        return filtered;
    }

    /**
     * Filter out findings from ignored rules.
     */
    private List<Finding> filterFindingsByConfig(List<Finding> findings, ReviewConfig config) {
        if (config.getIgnoreRules().isEmpty()) return findings;

        List<Finding> filtered = new ArrayList<>();
        for (Finding f : findings) {
            boolean ignored = false;
            for (String rule : config.getIgnoreRules()) {
                if (f.getSource() != null && f.getSource().equalsIgnoreCase(rule)) {
                    ignored = true;
                    break;
                }
                if (f.getCategory() != null && f.getCategory().equalsIgnoreCase(rule)) {
                    ignored = true;
                    break;
                }
            }
            if (!ignored) {
                filtered.add(f);
            }
        }
        return filtered;
    }

    /**
     * Merge, rank, and publish review findings.
     */
    private Mono<Void> publishReviewWithFindings(PullRequestContext prContext, ReviewConfig config, List<Finding> findings) {
        log.debug("Merging and ranking {} findings", findings.size());

        List<Finding> rankedFindings = findingMerger.mergeAndRank(findings);
        log.info("Final {} findings after deduplication and ranking", rankedFindings.size());

        // Determine autoApprove: per-repo config overrides global
        boolean autoApprove = config.getAutoApprove() != null
                ? config.getAutoApprove()
                : appProperties.isAutoApprove();

        boolean inlineComments = config.getInlineComments() != null
                ? config.getInlineComments()
                : appProperties.isInlineComments();

        log.debug("Publishing review to {}/{}/PR#{} (autoApprove={}, inline={})",
                prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(),
                autoApprove, inlineComments);

        return reviewPublisher.publishReview(
                        prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(),
                        rankedFindings, autoApprove, inlineComments, prContext.getInstallationId())
                .doOnSuccess(v -> log.info("Review published successfully for {}/{}/PR#{}",
                        prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber()))
                .doOnError(e -> log.error("Error publishing review for {}/{}/PR#{}",
                        prContext.getOwner(), prContext.getRepo(), prContext.getPrNumber(), e));
    }
}
