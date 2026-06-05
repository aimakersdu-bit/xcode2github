package com.example.adapter.controller;

import com.example.adapter.model.GitHubBranch;
import com.example.adapter.model.GitHubContent;
import com.example.adapter.service.GitService;
import com.example.adapter.service.GitService.FileEntry;
import com.example.adapter.util.GitHubContentFactory;
import com.example.adapter.util.UrlBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
public class GitHubController {

    private final GitService gitService;
    private final GitHubContentFactory contentFactory;
    private final UrlBuilder urlBuilder;

    private boolean initialized = false;

    public GitHubController(GitService gitService, GitHubContentFactory contentFactory, UrlBuilder urlBuilder) {
        this.gitService = gitService;
        this.contentFactory = contentFactory;
        this.urlBuilder = urlBuilder;
    }

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
            HttpServletRequest request) {

        ensureInitialized();

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        System.out.println("Requested content for path: " + subPath);

        try {
            if (subPath.isEmpty()) {
                return ResponseEntity.ok(listDirectory(owner, repo, subPath));
            }

            try {
                return ResponseEntity.ok(listDirectory(owner, repo, subPath));
            } catch (IOException e) {
                // Not a directory — fall through to file handling
            }

            byte[] contentBytes = gitService.getFileContent(subPath);
            return ResponseEntity.ok(contentFactory.fromFileContent(owner, repo, subPath, contentBytes));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("message", "Not Found"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    private List<GitHubContent> listDirectory(String owner, String repo, String subPath) throws IOException {
        List<FileEntry> entries = gitService.listFiles(subPath);
        List<GitHubContent> response = new ArrayList<>();
        for (FileEntry entry : entries) {
            if (entry.getName().equals(".git")) continue;
            response.add(contentFactory.fromDirectoryEntry(owner, repo, entry));
        }
        return response;
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
        commit.setUrl(urlBuilder.commitUrl(owner, repo, "mock-sha"));
        master.setCommit(commit);

        return ResponseEntity.ok(Collections.singletonList(master));
    }
}
