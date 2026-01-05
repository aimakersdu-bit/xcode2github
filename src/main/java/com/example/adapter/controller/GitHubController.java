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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@RestController
public class GitHubController {

    @Autowired
    private GitService gitService;

    private boolean initialized = false;

    private synchronized void ensureInitialized() {
        if (!initialized) {
            try {
                gitService.initRepo();
                initialized = true;
            } catch (Exception e) {
                System.err.println("Error initializing repo: " + e.getMessage());
            }
        }
    }

    @GetMapping("/repos/{owner}/{repo}/contents/**")
    public ResponseEntity<?> getContents(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false) String ref,
            HttpServletRequest request) {

        ensureInitialized();

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        System.out.println("Requested content for path: '" + subPath + "' ref: " + ref);

        try {
            // Check if it is a directory by trying to list it
            try {
                List<FileEntry> entries = gitService.listFiles(subPath, ref);
                // It's a directory
                List<GitHubContent> response = new ArrayList<>();
                for (FileEntry entry : entries) {
                    if (entry.getName().equals(".git")) continue;
                    response.add(mapToGitHubContent(owner, repo, entry, ref));
                }
                return ResponseEntity.ok(response);
            } catch (IOException e) {
                // Not a directory (or doesn't exist), try as file
                 if (e.getMessage().contains("Path is not a directory")) {
                     // Try to read as file
                 } else if (e.getMessage().contains("Path not found")) {
                     // Could be file, or really not found
                     // Let's try to read as file
                 } else {
                     throw e;
                 }
            }

            // Try file
            byte[] contentBytes = gitService.getFileContent(subPath, ref);
            GitHubContent content = mapToGitHubContent(owner, repo, subPath, contentBytes, ref);
            return ResponseEntity.ok(content);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("message", e.getMessage()));
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
        content.setSha(entry.getSha());

        String refPart = ref != null ? "?ref=" + ref : "";

        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath() + refPart);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/" + (ref != null ? ref : "master") + "/" + entry.getPath());
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/" + entry.getSha());
        content.setDownload_url("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath() + refPart);

        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf(content.getUrl());
        links.setGit(content.getGit_url());
        links.setHtml(content.getHtml_url());
        content.set_links(links);

        return content;
    }

    private GitHubContent mapToGitHubContent(String owner, String repo, String path, byte[] bytes, String ref) {
        GitHubContent content = new GitHubContent();
        String name = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
        content.setName(name);
        content.setPath(path);
        content.setSize(bytes.length);
        content.setType("file");
        // We don't have SHA here easily unless we fetch it from GitService again or return it with bytes.
        // For now, we can calculate it or ignore it.
        // Or we can modify GitService.getFileContent to return an object with SHA.
        // But for simplicity, we use "sha-calculated" or similar, or better:
        // Git SHA-1 is SHA1("blob " + size + "\0" + content)
        content.setSha("sha-placeholder");

        content.setEncoding("base64");
        content.setContent(Base64.getEncoder().encodeToString(bytes));

        String refPart = ref != null ? "?ref=" + ref : "";

        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + path + refPart);
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/" + (ref != null ? ref : "master") + "/" + path);
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/sha-placeholder");
        content.setDownload_url("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + path + refPart);

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
        ensureInitialized();
        try {
            List<String> branchNames = gitService.listBranches();
            List<GitHubBranch> branches = new ArrayList<>();
            for (String name : branchNames) {
                GitHubBranch branch = new GitHubBranch();
                branch.setName(name);
                branch.setProtectedBranch(false);
                GitHubBranch.Commit commit = new GitHubBranch.Commit();
                commit.setSha("sha-" + name); // Ideally we get the commit SHA too
                commit.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/commits/" + name);
                branch.setCommit(commit);
                branches.add(branch);
            }
            return ResponseEntity.ok(branches);
        } catch (Exception e) {
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
