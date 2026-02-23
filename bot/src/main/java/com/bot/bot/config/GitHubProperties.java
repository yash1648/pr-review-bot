package com.bot.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {
    private String appId;
    private String clientId;
    private String webhookSecret;
    private String privateKeyPath;
    private String apiUrl = "https://api.github.com";
}
