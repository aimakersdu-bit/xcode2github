package com.example.adapter.controller;

import com.example.adapter.model.GitHubBranch;
import com.example.adapter.model.GitHubContent;
import com.example.adapter.service.GitService;
import com.example.adapter.service.GitService.FileEntry;
import com.example.adapter.service.GitService.FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
            }
        }
    }

    @GetMapping("/raw/{owner}/{repo}/**")
    public ResponseEntity<Resource> getRawContent(
            @PathVariable String owner,
            @PathVariable String repo,
            HttpServletRequest request) {

        ensureInitialized();

        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String subPath = new AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);

        // Handling /raw/{owner}/{repo}/path/to/file
        // extractPathWithinPattern will give path/to/file

        try {
            byte[] content = gitService.getFileContent(subPath);
            ByteArrayResource resource = new ByteArrayResource(content);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + Path.of(subPath).getFileName().toString() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
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

        // Remove trailing slash if present
        if (subPath.endsWith("/")) {
            subPath = subPath.substring(0, subPath.length() - 1);
        }

        System.out.println("Requested content for path: '" + subPath + "'");

        try {
            FileType type = gitService.getFileType(subPath);

            // If path is empty, treat as root directory
            if (subPath.isEmpty()) {
                type = FileType.DIRECTORY;
            }

            if (type == FileType.DIRECTORY) {
                List<FileEntry> entries = gitService.listFiles(subPath);
                List<GitHubContent> response = new ArrayList<>();
                for (FileEntry entry : entries) {
                    if (entry.getName().equals(".git")) continue;
                    response.add(mapToGitHubContent(owner, repo, entry));
                }
                return ResponseEntity.ok(response);
            } else if (type == FileType.FILE) {
                byte[] contentBytes = gitService.getFileContent(subPath);
                GitHubContent content = mapToGitHubContent(owner, repo, subPath, contentBytes);
                return ResponseEntity.ok(content);
            } else {
                 return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("message", "Not Found"));
            }

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
        content.setSha("mock-sha"); // Ideally calculate SHA
        content.setUrl("http://localhost:8080/repos/" + owner + "/" + repo + "/contents/" + entry.getPath());
        content.setHtml_url("http://localhost:8080/" + owner + "/" + repo + "/blob/master/" + entry.getPath());
        content.setGit_url("http://localhost:8080/repos/" + owner + "/" + repo + "/git/blobs/mock-sha");
        content.setDownload_url("http://localhost:8080/raw/" + owner + "/" + repo + "/" + entry.getPath());

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
        content.setDownload_url("http://localhost:8080/raw/" + owner + "/" + repo + "/" + path);

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
