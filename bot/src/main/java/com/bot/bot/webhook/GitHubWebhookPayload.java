package com.bot.bot.webhook;

import com.google.gson.JsonObject;
import lombok.Data;

@Data
public class GitHubWebhookPayload {
    private String action;
    private JsonObject pullRequest;
    private JsonObject repository;
    private JsonObject sender;
    private String eventType;

    public static class PullRequest {
        private int number;
        private String title;
        private String body;
        private String state;
        private Head head;
        private Base base;

        @Data
        public static class Head {
            private String sha;
            private Ref ref;
        }

        @Data
        public static class Base {
            private Ref ref;
        }

        @Data
        public static class Ref {
            private String label;
            private String ref;
            private Repository repo;
        }

        @Data
        public static class Repository {
            private String name;
            private Owner owner;
        }

        @Data
        public static class Owner {
            private String login;
        }
    }
}

