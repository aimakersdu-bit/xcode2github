package com.example.adapter.model;

import lombok.Data;

@Data
public class GitHubBranch {
    private String name;
    private Commit commit;
    private boolean protectedBranch;

    @Data
    public static class Commit {
        private String sha;
        private String url;
    }
}
