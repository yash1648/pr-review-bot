package com.bot.bot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

/**
 * Loads per-repo .prreview.yaml configuration from the repository's default branch.
 * Uses raw GitHub content URL for simplicity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepoConfigLoader {

    private static final String RAW_URL = "https://raw.githubusercontent.com/%s/%s/HEAD/.prreview.yaml";

    private final WebClient webClient;

    /**
     * Fetch and parse .prreview.yaml from the repo.
     * Returns empty config (all nulls) if file doesn't exist or can't be parsed.
     */
    public Mono<ReviewConfig> loadConfig(String owner, String repo) {
        String url = String.format(RAW_URL, owner, repo);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseYaml)
                .onErrorResume(e -> {
                    log.debug("No .prreview.yaml found for {}/{} ({}), using defaults",
                            owner, repo, e.getMessage());
                    return Mono.just(new ReviewConfig());
                });
    }

    @SuppressWarnings("unchecked")
    ReviewConfig parseYaml(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return new ReviewConfig();
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> raw = yaml.load(yamlContent);
            if (raw == null || raw.isEmpty()) {
                return new ReviewConfig();
            }

            ReviewConfig config = new ReviewConfig();

            if (raw.containsKey("enabled")) config.setEnabled(toBoolean(raw.get("enabled")));
            if (raw.containsKey("auto_approve")) config.setAutoApprove(toBoolean(raw.get("auto_approve")));
            if (raw.containsKey("inline_comments")) config.setInlineComments(toBoolean(raw.get("inline_comments")));
            if (raw.containsKey("review_summary")) config.setReviewSummary(toBoolean(raw.get("review_summary")));
            if (raw.containsKey("llm_model")) config.setLlmModel(String.valueOf(raw.get("llm_model")));

            if (raw.containsKey("ignore_paths")) {
                config.setIgnorePaths(((java.util.List<String>) raw.get("ignore_paths")));
            }
            if (raw.containsKey("ignore_rules")) {
                config.setIgnoreRules(((java.util.List<String>) raw.get("ignore_rules")));
            }

            log.debug("Loaded .prreview.yaml config: {}", config);
            return config;
        } catch (Exception e) {
            log.warn("Failed to parse .prreview.yaml, using defaults", e);
            return new ReviewConfig();
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return null;
    }
}
