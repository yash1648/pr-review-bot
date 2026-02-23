package com.bot.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private long maxDiffSizeBytes = 1048576L; // 1MB
    private int maxFilesPerPr = 50;
    private boolean heuristicsEnabled = true;
    private boolean llmEnabled = true;
    private boolean enableCommentDeletion = false;
}
