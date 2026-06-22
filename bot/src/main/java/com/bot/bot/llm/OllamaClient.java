package com.bot.bot.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.bot.bot.config.LLMProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaClient implements LLMClient {
    private final LLMProperties llmProperties;
    private final WebClient webClient;
    private final Gson gson;

    public OllamaClient(LLMProperties llmProperties, WebClient webClient, Gson gson) {
        this.llmProperties = llmProperties;
        this.webClient = webClient;
        this.gson = gson;
    }

    @Override
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

}

