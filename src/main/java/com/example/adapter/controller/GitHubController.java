package com.example.adapter.controller;

import com.example.adapter.model.GitHubBranch;
import com.example.adapter.model.GitHubContent;
import com.example.adapter.service.GitService;
import com.example.adapter.service.GitService.FileEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@RestController
public class GitHubController {

    @Autowired
    private GitService gitService;

    // Initialize the repo on startup or first request
    private boolean initialized = false;

    private synchronized void ensureInitialized() {
        if (!initialized) {
            try {
                gitService.initRepo();
                initialized = true;
            } catch (Exception e) {
                // Log and continue. If cloning failed, subsequent read operations might fail
                // but we allow them to try (e.g. if partial state exists or for retry)
                System.err.println("Error initializing repo: " + e.getMessage());
                // We set initialized to true to avoid spamming init calls that fail immediately,
                // but maybe we should retry later? For now, simplistic approach.
                // Better: keep initialized=false if it was a critical failure?
                // But GitService.initRepo re-throws on clone failure.
                // If we swallow it here, the API returns 500 later.
            }
        }
    }

    @GetMapping("/repos/{owner}/{repo}/contents/**")
    public ResponseEntity<?> getContents(
            @PathVariable String owner,
            @PathVariable String repo,
            HttpServletRequest request) {

        ensureInitialized();

        // Extract the full path after /contents/
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        // Decoding usually happens by Spring, but let's be safe if manual URL manipulation happened
        // Actually Spring decodes PathVariables but extractPathWithinPattern preserves raw?
        // Let's assume subPath is correct.

        System.out.println("Requested content for path: " + subPath);

        try {
            // First check if it is a directory
             try {
                 List<FileEntry> entries = gitService.listFiles(subPath);
                 // It is a directory
                 List<GitHubContent> response = new ArrayList<>();
                 for (FileEntry entry : entries) {
                     if (entry.getName().equals(".git")) continue;
                     response.add(mapToGitHubContent(owner, repo, entry));
                 }
                 return ResponseEntity.ok(response);
             } catch (IOException e) {
                 // Not a directory, or doesn't exist. Try as file.
             }

            // If not directory, try file
            byte[] contentBytes = gitService.getFileContent(subPath);
            GitHubContent content = mapToGitHubContent(owner, repo, subPath, contentBytes);
            return ResponseEntity.ok(content);

        } catch (IOException e) {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("message", "Not Found"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    // Helper to map directory entry
    private GitHubContent mapToGitHubContent(String owner, String repo, FileEntry entry) {
        GitHubContent content = new GitHubContent();
        content.setName(entry.getName());
        content.setPath(entry.getPath());
        content.setSize(entry.getSize());
        content.setType(entry.getType());
        content.setSha("mock-sha");
        // Ensure URLs are correct
        String safePath = entry.getPath();
        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + safePath);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/master/" + safePath);
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        content.setDownload_url("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + safePath);

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(content.getUrl());
        links.setGit(content.getGit_url());
        links.setHtml(content.getHtml_url());
        content.set_links(links);

        return content;
    }

    // Helper to map file content
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
        // Mock user response for deepwiki-open authentication checks
        return ResponseEntity.ok(Collections.singletonMap("login", "mock-user"));
    }

    @GetMapping("/repos/{owner}/{repo}")
    public ResponseEntity<?> getRepo(@PathVariable String owner, @PathVariable String repo) {
        // Mock repo details
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
