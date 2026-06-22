package com.bot.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LLMProperties {
    /** LLM provider: "ollama" or "nvidia-nim" */
    private String provider = "ollama";
    private String model = "qwen2.5-coder:7b";
    private String baseUrl = "http://localhost:11434";
    private int timeoutSeconds = 60;
    private boolean enabled = true;
    /** API key for providers that require one (e.g., NVIDIA NIM) */
    private String apiKey = "";
}
