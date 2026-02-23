package com.bot.bot;

import com.bot.bot.config.GithubConfig;
import com.bot.bot.config.LLMConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for the Bot application.
 * This class bootstraps the Spring Boot application and enables configuration properties for Github and LLM.

 */

@SpringBootApplication
@EnableAsync
public class BotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
    }

}
