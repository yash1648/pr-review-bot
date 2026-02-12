package com.bot.bot.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/***
 * Github App configuration properties.
 * * These properties are loaded from application.properties or application.yml file.
 * * The prefix for these properties is "github.app".
 */
@Configuration
@ConfigurationProperties(prefix = "github.app")
public class GithubConfig {
    private long id;
    private String clientId;
    private String privateKeyPath;
    private String webhookSecret;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
