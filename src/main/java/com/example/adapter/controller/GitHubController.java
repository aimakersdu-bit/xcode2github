package com.example.adapter.controller;

import com.example.adapter.exception.RepositoryNotInitializedException;
import com.example.adapter.model.GitHubBranch;
import com.example.adapter.model.GitHubContent;
import com.example.adapter.service.GitService;
import com.example.adapter.service.GitService.FileEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@RestController
public class GitHubController {

    private static final Logger log = LoggerFactory.getLogger(GitHubController.class);

    @Autowired
    private GitService gitService;

    private boolean initialized = false;
    private Exception lastInitError = null;

    private synchronized void ensureInitialized() {
        if (!initialized) {
            try {
                gitService.initRepo();
                initialized = true;
                lastInitError = null;
            } catch (Exception e) {
                lastInitError = e;
                log.error("Failed to initialize repository: {}", e.getMessage(), e);
                throw new RepositoryNotInitializedException(
                        "Repository initialization failed: " + e.getMessage(), e);
            }
        }
    }

    @GetMapping("/repos/{owner}/{repo}/contents/**")
    public ResponseEntity<?> getContents(
            @PathVariable String owner,
            @PathVariable String repo,
            HttpServletRequest request) {

        ensureInitialized();

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        log.debug("Requested content for path: {}", subPath);

        try {
            if (subPath.isEmpty() || gitService.isDirectory(subPath)) {
                List<FileEntry> entries = gitService.listFiles(subPath);
                List<GitHubContent> response = new ArrayList<>();
                for (FileEntry entry : entries) {
                    if (entry.getName().equals(".git")) continue;
                    response.add(mapToGitHubContent(owner, repo, entry));
                }
                return ResponseEntity.ok(response);
            }

            byte[] contentBytes = gitService.getFileContent(subPath);
            GitHubContent content = mapToGitHubContent(owner, repo, subPath, contentBytes);
            return ResponseEntity.ok(content);

        } catch (IOException e) {
            log.warn("Content not found for path '{}': {}", subPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", "Not Found: " + e.getMessage()));
        }
    }

    private GitHubContent mapToGitHubContent(String owner, String repo, FileEntry entry) {
        GitHubContent content = new GitHubContent();
        content.setName(entry.getName());
        content.setPath(entry.getPath());
        content.setSize(entry.getSize());
        content.setType(entry.getType());
        content.setSha("mock-sha");
        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath());
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/master/" + entry.getPath());
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        content.setDownload_url("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath());

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(content.getUrl());
        links.setGit(content.getGit_url());
        links.setHtml(content.getHtml_url());
        content.set_links(links);

        return content;
    }

    private GitHubContent mapToGitHubContent(String owner, String repo, String path, byte[] bytes) {
        GitHubContent content = new GitHubContent();
        Path p = Path.of(path);
        content.setName(p.getFileName().toString());
        content.setPath(path);
        content.setSize(bytes.length);
        content.setType("file");
        content.setSha("mock-sha");
        content.setEncoding("base64");
        content.setContent(Base64.getEncoder().encodeToString(bytes));

        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + path);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/master/" + path);
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        content.setDownload_url("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + path);

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(content.getUrl());
        links.setGit(content.getGit_url());
        links.setHtml(content.getHtml_url());
        content.set_links(links);

        return content;
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUser() {
        return ResponseEntity.ok(Collections.singletonMap("login", "mock-user"));
    }

    @GetMapping("/repos/{owner}/{repo}")
    public ResponseEntity<?> getRepo(@PathVariable String owner, @PathVariable String repo) {
        return ResponseEntity.ok(Collections.singletonMap("default_branch", "master"));
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GitHubBranch>> getBranches(@PathVariable String owner, @PathVariable String repo) {
        GitHubBranch master = new GitHubBranch();
        master.setName("master");
        master.setProtectedBranch(false);
        GitHubBranch.Commit commit = new GitHubBranch.Commit();
        commit.setSha("mock-sha");
        commit.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/commits/mock-sha");
        master.setCommit(commit);

        return ResponseEntity.ok(Collections.singletonList(master));
    }
}
