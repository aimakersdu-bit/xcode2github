package com.example.adapter.controller;

import com.example.adapter.model.GitHubBranch;
import com.example.adapter.model.GitHubContent;
import com.example.adapter.service.GitService;
import com.example.adapter.service.GitService.FileEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.http.HttpServletRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
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

    @GetMapping("/repos/{owner}/{repo}/contents/**")
    public ResponseEntity<?> getContents(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false, defaultValue = "master") String ref,
            HttpServletRequest request) {

        ensureInitialized();

        // Extract the full path after /contents/
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        System.out.println("Requested content for path: " + subPath + " ref: " + ref);

        try {
            // First try as directory
            try {
                List<FileEntry> entries = gitService.listFiles(ref, subPath);
                List<GitHubContent> response = new ArrayList<>();
                for (FileEntry entry : entries) {
                    if (entry.getName().equals(".git")) continue;
                    response.add(mapToGitHubContent(owner, repo, entry, ref));
                }
                return ResponseEntity.ok(response);
            } catch (FileNotFoundException | IllegalArgumentException e) {
                // Not a directory or not found, try as file
            } catch (IOException e) {
                 if (e.getMessage() != null && e.getMessage().contains("Path is not a directory")) {
                     // Proceed to file check
                 } else {
                     throw e;
                 }
            }

            // Try as file
            byte[] contentBytes = gitService.getFileContent(ref, subPath);
            GitHubContent content = mapToGitHubContent(owner, repo, subPath, contentBytes, ref);
            return ResponseEntity.ok(content);

        } catch (FileNotFoundException | IllegalArgumentException e) {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("message", "Not Found"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/raw/{owner}/{repo}/{ref}/**")
    public ResponseEntity<byte[]> getRawContent(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String ref,
            HttpServletRequest request) {

        ensureInitialized();

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        try {
            byte[] content = gitService.getFileContent(ref, subPath);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN) // Or detect mime type
                    .body(content);
        } catch (FileNotFoundException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper to map directory entry
    private GitHubContent mapToGitHubContent(String owner, String repo, FileEntry entry, String ref) {
        GitHubContent content = new GitHubContent();
        content.setName(entry.getName());
        content.setPath(entry.getPath());
        content.setSize(entry.getSize());
        content.setType(entry.getType());
        content.setSha("mock-sha");
        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath() + "?ref=" + ref);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/" + ref + "/" + entry.getPath());
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        content.setDownload_url("http://localhost:8080/raw/" + owner + "/" + repo + "/" + ref + "/" + entry.getPath());

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(content.getUrl());
        links.setGit(content.getGit_url());
        links.setHtml(content.getHtml_url());
        content.set_links(links);

        return content;
    }

    // Helper to map file content
    private GitHubContent mapToGitHubContent(String owner, String repo, String path, byte[] bytes, String ref) {
        GitHubContent content = new GitHubContent();
        Path p = Path.of(path);
        content.setName(p.getFileName().toString());
        content.setPath(path);
        content.setSize(bytes.length);
        content.setType("file");
        content.setSha("mock-sha");
        content.setEncoding("base64");
        content.setContent(Base64.getEncoder().encodeToString(bytes));

        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/" + ref + "/" + path);
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        content.setDownload_url("http://localhost:8080/raw/" + owner + "/" + repo + "/" + ref + "/" + path);

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
