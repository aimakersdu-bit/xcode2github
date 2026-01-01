package com.example.adapter.model;

import lombok.Data;
import java.util.List;

@Data
public class GitHubContent {
    private String name;
    private String path;
    private String sha; // Mocked SHA
    private long size;
    private String url;
    private String html_url;
    private String git_url;
    private String download_url;
    private String type; // "file" or "dir"
    private String content; // Base64 encoded content
    private String encoding; // "base64"
    private Links _links;

    @Data
    public static class Links {
        private String self;
        private String git;
        private String html;
    }
}
