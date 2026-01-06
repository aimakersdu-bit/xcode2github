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
import java.util.Map;

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
                System.err.println("Error initializing repo: " + e.getMessage());
            }
        } else {
            // Check for updates periodically
            gitService.checkAndFetchUpdates();
        }
    }

    @GetMapping("/repos/{owner}/{repo}/contents/**")
    public ResponseEntity<?> getContents(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false) String ref,
            HttpServletRequest request) {

        ensureInitialized();

        // Extract the full path after /contents/
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        System.out.println("Requested content for path: " + subPath + " ref: " + ref);

        try {
            // Check if it's a file or directory
            // We can first try to list it as a directory.
            // If it returns entries, it's a directory.
            // If it returns empty, it might be an empty dir or a file or non-existent.

            // However, our GitService logic for listFiles might return empty for file too if not careful,
            // but currently it checks if it's a subtree.

            // Let's try to list first.
            List<FileEntry> entries = gitService.listFiles(subPath, ref);

            // If entries found, it's a directory
            if (!entries.isEmpty()) {
                List<GitHubContent> response = new ArrayList<>();
                for (FileEntry entry : entries) {
                    if (entry.getName().equals(".git")) continue;
                    response.add(mapToGitHubContent(owner, repo, entry, ref));
                }
                return ResponseEntity.ok(response);
            }

            // If empty, it could be an empty dir or a file.
            // Try to read as file.
            try {
                byte[] contentBytes = gitService.getFileContent(subPath, ref);
                // If successful, it's a file.
                GitHubContent content = mapToGitHubContent(owner, repo, subPath, contentBytes, ref);
                return ResponseEntity.ok(content);
            } catch (IOException e) {
                if (e.getMessage().startsWith("Path is a directory")) {
                     // It was a directory but empty
                     return ResponseEntity.ok(Collections.emptyList());
                }
                throw e;
            }

        } catch (IOException e) {
             // If both failed, it's 404
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("message", "Not Found: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    private GitHubContent mapToGitHubContent(String owner, String repo, FileEntry entry, String ref) {
        GitHubContent content = new GitHubContent();
        content.setName(entry.getName());
        content.setPath(entry.getPath());
        content.setSize(entry.getSize());
        content.setType(entry.getType());
        content.setSha(entry.getSha()); // Real SHA from GitService

        String refParam = (ref != null) ? "?ref=" + ref : "";

        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath() + refParam);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/" + (ref != null ? ref : "master") + "/" + entry.getPath());
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/" + entry.getSha());
        content.setDownload_url("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath() + refParam);

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(content.getUrl());
        links.setGit(content.getGit_url());
        links.setHtml(content.getHtml_url());
        content.set_links(links);

        return content;
    }

    private GitHubContent mapToGitHubContent(String owner, String repo, String path, byte[] bytes, String ref) {
        GitHubContent content = new GitHubContent();
        Path p = Path.of(path);
        content.setName(p.getFileName().toString());
        content.setPath(path);
        content.setSize(bytes.length);
        content.setType("file");
        // We don't have SHA here easily without another lookup, but that's okay for now.
        content.setSha("sha-placeholder");
        content.setEncoding("base64");
        content.setContent(Base64.getEncoder().encodeToString(bytes));

        String refParam = (ref != null) ? "?ref=" + ref : "";

        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + path + refParam);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/" + (ref != null ? ref : "master") + "/" + path);
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/sha-placeholder");
        content.setDownload_url("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + path + refParam);

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
        // We could expose default_branch from GitService if we want
        return ResponseEntity.ok(Collections.singletonMap("default_branch", "master"));
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GitHubBranch>> getBranches(@PathVariable String owner, @PathVariable String repo) {
        ensureInitialized();
        List<String> branchNames = gitService.getBranches();
        List<GitHubBranch> branches = new ArrayList<>();

        for (String name : branchNames) {
            GitHubBranch branch = new GitHubBranch();
            branch.setName(name);
            branch.setProtectedBranch(false);
            GitHubBranch.Commit commit = new GitHubBranch.Commit();
            // In a real implementation we would fetch the commit SHA for each branch
            commit.setSha("sha-placeholder");
            commit.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/commits/" + name);
            branch.setCommit(commit);
            branches.add(branch);
        }

        return ResponseEntity.ok(branches);
    }
}
