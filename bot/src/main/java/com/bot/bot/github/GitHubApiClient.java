package com.bot.bot.github;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bot.bot.config.GitHubProperties;
import com.bot.bot.domain.PullRequestContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiClient {
    private final GitHubProperties gitHubProperties;
    private final GitHubJwtGenerator jwtGenerator;
    private final WebClient webClient;
    private final Gson gson = new Gson();

    public Mono<PullRequestContext> fetchPullRequestContext(JsonObject prData) {
        try {
            String owner = prData.getAsJsonObject("repository")
                    .getAsJsonObject("owner").get("login").getAsString();
            String repo = prData.getAsJsonObject("repository").get("name").getAsString();
            int prNumber = prData.getAsJsonObject("pull_request").get("number").getAsInt();
            String title = prData.getAsJsonObject("pull_request").get("title").getAsString();
            String description = prData.getAsJsonObject("pull_request").get("body").getAsString();
            String authorLogin = prData.getAsJsonObject("pull_request")
                    .getAsJsonObject("user").get("login").getAsString();

            String baseRef = prData.getAsJsonObject("pull_request")
                    .getAsJsonObject("base").getAsJsonObject("ref").get("ref").getAsString();
            String headRef = prData.getAsJsonObject("pull_request")
                    .getAsJsonObject("head").getAsJsonObject("ref").get("ref").getAsString();
            String commitSha = prData.getAsJsonObject("pull_request")
                    .getAsJsonObject("head").get("sha").getAsString();

            return fetchDiff(owner, repo, prNumber)
                    .map(diff -> PullRequestContext.builder()
                            .owner(owner)
                            .repo(repo)
                            .prNumber(prNumber)
                            .title(title)
                            .description(description)
                            .authorLogin(authorLogin)
                            .baseRef(baseRef)
                            .headRef(headRef)
                            .commitSha(commitSha)
                            .build());
        } catch (Exception e) {
            log.error("Error parsing PR data", e);
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

    public Mono<Void> publishReview(String owner, String repo, int prNumber,
                                    String reviewBody, String event) {
        String url = String.format("%s/repos/%s/%s/pulls/%d/reviews",
                gitHubProperties.getApiUrl(), owner, repo, prNumber);

        JsonObject body = new JsonObject();
        body.addProperty("body", reviewBody);
        body.addProperty("event", event);

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + jwtGenerator.generateAppToken())
                .header("Accept", "application/vnd.github.v3+json")
                .bodyValue(body.toString())
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("Error publishing review for {}/{}/PR#{}", owner, repo, prNumber, e));
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