package com.bot.bot.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-repo configuration loaded from .prreview.yaml in the default branch.
 * Overrides global application properties when set.
 */
@Data
public class ReviewConfig {
    private Boolean enabled;
    private Boolean autoApprove;
    private Boolean inlineComments;
    private Boolean reviewSummary;
    private List<String> ignorePaths = new ArrayList<>();
    private List<String> ignoreRules = new ArrayList<>();
    private String llmModel;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
