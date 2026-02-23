package com.bot.bot.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.bot.bot.config.LLMProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaClient {
    private final LLMProperties llmProperties;
    private final WebClient webClient;
    private final Gson gson = new Gson();

    public Mono<String> generateCodeReview(String prompt) {
        if (!llmProperties.isEnabled()) {
            return Mono.just("LLM review disabled");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", llmProperties.getModel());
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("temperature", 0.7);

        String url = llmProperties.getBaseUrl() + "/api/generate";

        return webClient.post()
                .uri(url)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()))
                .map(response -> {
                    try {
                        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                        return jsonResponse.get("response").getAsString();
                    } catch (Exception e) {
                        log.error("Error parsing LLM response", e);
                        return "Error processing LLM response";
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Error calling Ollama LLM", e);
                    return Mono.just("LLM service unavailable");
                });
    }

    public boolean isAvailable() {
        try {
            String healthUrl = llmProperties.getBaseUrl() + "/api/tags";
            webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            log.debug("LLM service not available", e);
            return false;
        }
    }
}

