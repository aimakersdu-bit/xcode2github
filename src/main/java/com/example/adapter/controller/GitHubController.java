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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
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
                // Log and continue, maybe retry later
                System.err.println("Error initializing repo: " + e.getMessage());
                // In production, might want to return 503 Service Unavailable until initialized
            }
        }
    }

    @GetMapping("/repos/{owner}/{repo}/raw/**")
    public ResponseEntity<byte[]> getRawContent(
            @PathVariable String owner,
            @PathVariable String repo,
            HttpServletRequest request) {

        ensureInitialized();

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        try {
            byte[] content = gitService.getFileContent(subPath);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/octet-stream")
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
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

        System.out.println("Requested content for path: " + subPath);

        try {
            // Check if it's a file or directory
            // We use GitService to check.
            // Note: Since we don't have a fast "isDir" without checking filesystem, we might need logic.
            // But GitService.listDirectory throws if not dir.

            // Try as directory first? Or check file system?
            // The GitService works on local filesystem which is fast.

            // But wait, subPath could be empty for root.

            boolean isDir = false;
            if (subPath.isEmpty()) {
                isDir = true;
            } else {
                // Check locally
                // Ideally GitService should expose "getType"
                // For now, I'll access the implementation detail or improve GitService
                // Let's improve GitService by assuming we can check via it.
                // But simply:
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
                     // Not a directory, try as file
                 }
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
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        GitHubContent content = new GitHubContent();
        content.setName(entry.getName());
        content.setPath(entry.getPath());
        content.setSize(entry.getSize());
        content.setType(entry.getType());
        content.setSha("mock-sha");
        content.setUrl(baseUrl + "/repos/" + owner + "/" + repo + "/contents/" + entry.getPath());
        content.setHtml_url(baseUrl + "/" + owner + "/" + repo + "/blob/master/" + entry.getPath());
        content.setGit_url(baseUrl + "/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        if ("file".equals(entry.getType())) {
            content.setDownload_url(baseUrl + "/repos/" + owner + "/" + repo + "/raw/" + entry.getPath());
        } else {
            content.setDownload_url(null);
        }

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(content.getUrl());
        links.setGit(content.getGit_url());
        links.setHtml(content.getHtml_url());
        content.set_links(links);

        return content;
    }

    // Helper to map file content
    private GitHubContent mapToGitHubContent(String owner, String repo, String path, byte[] bytes) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        GitHubContent content = new GitHubContent();
        Path p = Path.of(path);
        content.setName(p.getFileName().toString());
        content.setPath(path);
        content.setSize(bytes.length);
        content.setType("file");
        content.setSha("mock-sha");
        content.setEncoding("base64");
        content.setContent(Base64.getEncoder().encodeToString(bytes));

        content.setUrl(baseUrl + "/repos/" + owner + "/" + repo + "/contents/" + path);
        content.setHtml_url(baseUrl + "/" + owner + "/" + repo + "/blob/master/" + path);
        content.setGit_url(baseUrl + "/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        content.setDownload_url(baseUrl + "/repos/" + owner + "/" + repo + "/raw/" + path);

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
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        GitHubBranch master = new GitHubBranch();
        master.setName("master");
        master.setProtectedBranch(false);
        GitHubBranch.Commit commit = new GitHubBranch.Commit();
        commit.setSha("mock-sha");
        commit.setUrl(baseUrl + "/repos/" + owner + "/" + repo + "/commits/mock-sha");
        master.setCommit(commit);

        return ResponseEntity.ok(Collections.singletonList(master));
    }
}
