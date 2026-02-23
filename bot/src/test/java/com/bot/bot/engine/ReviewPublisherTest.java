package com.bot.bot.engine;

import com.bot.bot.domain.Finding;
import com.bot.bot.github.GitHubApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewPublisherTest {

    @Test
    void publishesNoIssuesCommentWhenNoFindings() {
        GitHubApiClient client = Mockito.mock(GitHubApiClient.class);
        when(client.postComment(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.empty());

        ReviewPublisher publisher = new ReviewPublisher(client);

        publisher.publishReview("owner", "repo", 1, List.of()).block();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).postComment(Mockito.eq("owner"), Mockito.eq("repo"), Mockito.eq(1), bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains("No issues found"));
    }

    @Test
    void publishesFormattedReviewWhenFindingsPresent() {
        GitHubApiClient client = Mockito.mock(GitHubApiClient.class);
        when(client.postComment(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.empty());

        ReviewPublisher publisher = new ReviewPublisher(client);

        Finding finding = Finding.builder()
                .id("1")
                .filePath("file.java")
                .lineNumber(10)
                .severity("HIGH")
                .category("TEST")
                .message("message")
                .suggestion("suggestion")
                .source("HEURISTIC")
                .confidence(0.9)
                .precedenceScore(100)
                .build();

        publisher.publishReview("owner", "repo", 1, List.of(finding)).block();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).postComment(Mockito.eq("owner"), Mockito.eq("repo"), Mockito.eq(1), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("Code Review Analysis"));
        assertTrue(body.contains("HIGH"));
        assertTrue(body.contains("file.java"));
    }
}

