package com.bot.bot.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.bot.bot.config.LLMProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Client for NVIDIA NIM (NVIDIA Inference Microservices) API.
 * Uses the OpenAI-compatible /v1/chat/completions endpoint.
 *
 * Default base URL: http://localhost:8000
 * Hosted API: https://integrate.api.nvidia.com/v1  (set as base-url, api-key required)
 *
 * Requires:
 *   LLM_PROVIDER=nvidia-nim
 *   LLM_API_KEY=nvapi-... (required for hosted API, optional for self-hosted)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "nvidia-nim")
public class NvidiaNimClient implements LLMClient {
    private final LLMProperties llmProperties;
    private final WebClient webClient;
    private final Gson gson;

    public NvidiaNimClient(LLMProperties llmProperties, WebClient webClient, Gson gson) {
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

        // Build messages array (OpenAI-compatible chat format)
        JsonArray messages = new JsonArray();

        // System message for role/behavior
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an expert code reviewer. Provide concise, actionable feedback.");
        messages.add(systemMessage);

        // User message with the prompt
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 2048);

        String url = llmProperties.getBaseUrl() + "/v1/chat/completions";

        var spec = webClient.post()
                .uri(url)
                .bodyValue(requestBody.toString());

        // Add API key if configured (Bearer token auth)
        String apiKey = llmProperties.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            spec = spec.header("Authorization", "Bearer " + apiKey);
        }

        return spec.retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()))
                .map(response -> {
                    try {
                        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                        JsonArray choices = jsonResponse.getAsJsonArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            return choices.get(0).getAsJsonObject()
                                    .getAsJsonObject("message")
                                    .get("content")
                                    .getAsString();
                        }
                        return "No response content";
                    } catch (Exception e) {
                        log.error("Error parsing NVIDIA NIM response", e);
                        return "Error processing LLM response";
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Error calling NVIDIA NIM LLM", e);
                    return Mono.just("LLM service unavailable");
                });
    }
}
