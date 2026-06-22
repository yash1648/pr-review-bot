package com.bot.bot.github;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bot.bot.config.GitHubProperties;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.domain.ReviewComment;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiClient {
    private final GitHubProperties gitHubProperties;
    private final GitHubJwtGenerator jwtGenerator;
    private final WebClient webClient;
    private final Gson gson;

    public Mono<PullRequestContext> fetchPullRequestContext(JsonObject prData) {
        try {
            JsonObject repo = prData.getAsJsonObject("repository");
            JsonObject pr = prData.getAsJsonObject("pull_request");
            if (repo == null || pr == null) {
                return Mono.error(new IllegalArgumentException("Missing repository or pull_request in webhook payload"));
            }

            JsonObject repoOwner = repo.getAsJsonObject("owner");
            if (repoOwner == null) {
                return Mono.error(new IllegalArgumentException("Missing repository.owner in webhook payload"));
            }

            String owner = repoOwner.get("login").getAsString();
            String repoName = repo.get("name").getAsString();
            int prNumber = pr.get("number").getAsInt();
            String title = pr.get("title").getAsString();
            String description = pr.has("body") && !pr.get("body").isJsonNull() ? pr.get("body").getAsString() : "";
            JsonObject prUser = pr.has("user") && !pr.get("user").isJsonNull() ? pr.getAsJsonObject("user") : null;
            String authorLogin = prUser != null && prUser.has("login") && !prUser.get("login").isJsonNull()
                    ? prUser.get("login").getAsString() : "unknown";

            JsonObject base = pr.getAsJsonObject("base");
            JsonObject head = pr.getAsJsonObject("head");
            if (base == null || head == null) {
                return Mono.error(new IllegalArgumentException("Missing pull_request.base or pull_request.head in webhook payload"));
            }

            String baseRef = base.get("ref").getAsString();
            String headRef = head.get("ref").getAsString();
            String commitSha = head.get("sha").getAsString();

            return fetchDiff(owner, repoName, prNumber)
                    .map(diff -> PullRequestContext.builder()
                            .owner(owner)
                            .repo(repoName)
                            .prNumber(prNumber)
                            .title(title)
                            .description(description)
                            .authorLogin(authorLogin)
                            .baseRef(baseRef)
                            .headRef(headRef)
                            .commitSha(commitSha)
                            .build());
        } catch (Exception e) {
            log.error("Error parsing PR data for delivery", e);
            return Mono.error(e);
        }
    }

    public Mono<String> fetchDiff(String owner, String repo, int prNumber) {
        String url = String.format("%s/repos/%s/%s/pulls/%d",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        return webClient.get()
                .uri(url)
                .header("Accept", "application/vnd.github.v3.diff")
                .header("Authorization", "Bearer " + jwtGenerator.generateAppToken())
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("Error fetching diff for {}/{}/PR#{}", owner, repo, prNumber, e));
    }

    /**
     * Posts a PR review with an optional summary body and inline comments.
     * Uses the GitHub PR Review API which groups everything into a single review event.
     *
     * @param owner    repo owner
     * @param repo     repo name
     * @param prNumber PR number
     * @param body     summary body text (markdown)
     * @param event    REVIEW_EVENT: "APPROVE", "REQUEST_CHANGES", or "COMMENT"
     * @param comments list of inline comments (can be empty)
     */
    public Mono<Void> submitReview(String owner, String repo, int prNumber,
                                    String body, String event, List<ReviewComment> comments) {
        String url = String.format("%s/repos/%s/%s/pulls/%d/reviews",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        JsonObject reviewBody = new JsonObject();
        reviewBody.addProperty("body", body);
        reviewBody.addProperty("event", event);

        if (comments != null && !comments.isEmpty()) {
            JsonArray commentArray = new JsonArray();
            for (ReviewComment c : comments) {
                if (c.getPath() == null || c.getBody() == null) continue;
                JsonObject comment = new JsonObject();
                comment.addProperty("path", c.getPath());
                comment.addProperty("body", c.getBody());
                // Use line for both single-line and multi-line (end of range)
                if (c.getLine() > 0) {
                    comment.addProperty("line", c.getLine());
                    comment.addProperty("side", c.getSide() != null ? c.getSide() : "RIGHT");
                }
                if (c.getStartLine() > 0 && c.getStartLine() != c.getLine()) {
                    comment.addProperty("start_line", c.getStartLine());
                }
                commentArray.add(comment);
            }
            reviewBody.add("comments", commentArray);
        }

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + jwtGenerator.generateAppToken())
                .header("Accept", "application/vnd.github.v3+json")
                .bodyValue(reviewBody.toString())
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("Error submitting review for {}/{}/PR#{}", owner, repo, prNumber, e));
    }

    public Mono<Void> postComment(String owner, String repo, int prNumber, String body) {
        String url = String.format("%s/repos/%s/%s/issues/%d/comments",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        JsonObject comment = new JsonObject();
        comment.addProperty("body", body);

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + jwtGenerator.generateAppToken())
                .header("Accept", "application/vnd.github.v3+json")
                .bodyValue(comment.toString())
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("Error posting comment for {}/{}/PR#{}", owner, repo, prNumber, e));
    }
}
