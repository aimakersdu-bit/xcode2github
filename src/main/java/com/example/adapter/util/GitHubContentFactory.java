package com.example.adapter.util;

import com.example.adapter.model.GitHubContent;
import com.example.adapter.service.GitService.FileEntry;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Base64;

/**
 * Factory for constructing GitHubContent response objects.
 * Consolidates duplicated mapping logic from the controller.
 */
@Component
public class GitHubContentFactory {

    private final UrlBuilder urlBuilder;

    public GitHubContentFactory(UrlBuilder urlBuilder) {
        this.urlBuilder = urlBuilder;
    }

    /**
     * Build a GitHubContent representing a directory entry (no file body).
     */
    public GitHubContent fromDirectoryEntry(String owner, String repo, FileEntry entry) {
        GitHubContent content = new GitHubContent();
        content.setName(entry.getName());
        content.setPath(entry.getPath());
        content.setSize(entry.getSize());
        content.setType(entry.getType());
        content.setSha("mock-sha");
        populateUrls(content, owner, repo, entry.getPath());
        return content;
    }

    /**
     * Build a GitHubContent representing a file with Base64-encoded body.
     */
    public GitHubContent fromFileContent(String owner, String repo, String path, byte[] bytes) {
        GitHubContent content = new GitHubContent();
        content.setName(Path.of(path).getFileName().toString());
        content.setPath(path);
        content.setSize(bytes.length);
        content.setType("file");
        content.setSha("mock-sha");
        content.setEncoding("base64");
        content.setContent(Base64.getEncoder().encodeToString(bytes));
        populateUrls(content, owner, repo, path);
        return content;
    }

    private void populateUrls(GitHubContent content, String owner, String repo, String path) {
        String selfUrl = urlBuilder.contentsUrl(owner, repo, path);
        String htmlUrl = urlBuilder.htmlUrl(owner, repo, path);
        String gitUrl = urlBuilder.gitBlobUrl(owner, repo, "mock-sha");

        content.setUrl(selfUrl);
        content.setHtml_url(htmlUrl);
        content.setGit_url(gitUrl);
        content.setDownload_url(selfUrl);

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(selfUrl);
        links.setGit(gitUrl);
        links.setHtml(htmlUrl);
        content.set_links(links);
    }
}
