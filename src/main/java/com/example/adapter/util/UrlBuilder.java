package com.example.adapter.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized URL construction for GitHub-compatible API responses.
 * Eliminates hardcoded "http://localhost:8080" scattered across the controller.
 */
@Component
public class UrlBuilder {

    @Value("${adapter.base-url:http://localhost:8080}")
    private String baseUrl;

    public String contentsUrl(String owner, String repo, String path) {
        return baseUrl + "/repos/" + owner + "/" + repo + "/contents/" + path;
    }

    public String htmlUrl(String owner, String repo, String path) {
        return baseUrl + "/" + owner + "/" + repo + "/blob/master/" + path;
    }

    public String gitBlobUrl(String owner, String repo, String sha) {
        return baseUrl + "/repos/" + owner + "/" + repo + "/git/blobs/" + sha;
    }

    public String commitUrl(String owner, String repo, String sha) {
        return baseUrl + "/repos/" + owner + "/" + repo + "/commits/" + sha;
    }
}
