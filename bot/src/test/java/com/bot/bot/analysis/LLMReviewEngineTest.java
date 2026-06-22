package com.bot.bot.analysis;

import com.bot.bot.domain.ChangeChunk;
import com.bot.bot.domain.Finding;
import com.bot.bot.domain.PullRequestContext;
import com.bot.bot.llm.LLMClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LLMReviewEngineTest {

    @Test
    void returnsFindingsBasedOnLlmResponse() {
        LLMClient client = Mockito.mock(LLMClient.class);
        Mockito.when(client.generateCodeReview(Mockito.anyString()))
                .thenReturn(Mono.just("critical security issue detected"));

        LLMReviewEngine engine = new LLMReviewEngine(client);

        ChangeChunk chunk = ChangeChunk.builder()
                .filePath("file.java")
                .startLine(10)
                .addedLines(List.of("line"))
                .removedLines(Collections.emptyList())
                .changeType("MODIFIED")
                .context("")
                .build();

        PullRequestContext context = PullRequestContext.builder()
                .owner("owner")
                .repo("repo")
                .prNumber(1)
                .title("title")
                .description("description")
                .build();

        List<Finding> findings = engine.analyzeWithLLM(context, List.of(chunk)).block();

        assertFalse(findings.isEmpty());
        assertEquals("CRITICAL", findings.get(0).getSeverity());
    }

    @Test
    void returnsEmptyListWhenLlmErrors() {
        LLMClient client = Mockito.mock(LLMClient.class);
        Mockito.when(client.generateCodeReview(Mockito.anyString()))
                .thenReturn(Mono.error(new RuntimeException("error")));

        LLMReviewEngine engine = new LLMReviewEngine(client);

        ChangeChunk chunk = ChangeChunk.builder()
                .filePath("file.java")
                .startLine(10)
                .addedLines(List.of("line"))
                .removedLines(Collections.emptyList())
                .changeType("MODIFIED")
                .context("")
                .build();

        PullRequestContext context = PullRequestContext.builder()
                .owner("owner")
                .repo("repo")
                .prNumber(1)
                .title("title")
                .description("description")
                .build();

        List<Finding> findings = engine.analyzeWithLLM(context, List.of(chunk)).block();

        assertEquals(0, findings.size());
    }
}

