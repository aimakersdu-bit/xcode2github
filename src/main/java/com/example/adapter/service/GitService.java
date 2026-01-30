package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitService {

    @Value("${ezone.repo.url}")
    private String repoUrl;

    @Value("${ezone.repo.username}")
    private String username;

    @Value("${ezone.repo.password}")
    private String password;

    @Value("${ezone.repo.local-path}")
    private String localPath;

    private Git git;

    public void initRepo() throws IOException, GitAPIException {
        File repoDir = new File(localPath);
        boolean isNew = false;

        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            try {
                git = Git.open(repoDir);
                System.out.println("Opened existing repository.");
            } catch (Exception e) {
                System.err.println("Failed to open existing repo, re-initializing: " + e.getMessage());
                deleteDirectory(repoDir);
                isNew = true;
            }
        } else {
            isNew = true;
        }

        if (isNew) {
            if (!repoDir.exists()) {
                repoDir.mkdirs();
            }
            git = Git.init().setDirectory(repoDir).call();
            System.out.println("Initialized new repository.");
        }

        // Configure SSL Verify to false for all cases
        StoredConfig config = git.getRepository().getConfig();
        config.setBoolean("http", null, "sslVerify", false);
        config.save();

        // Ensure remote is set (if new)
        if (isNew) {
            try {
                git.remoteAdd().setName("origin").setUri(new URIish(repoUrl)).call();
            } catch (URISyntaxException e) {
                throw new IOException("Invalid repo URL", e);
            }
        }

        // Pull changes
        try {
            System.out.println("Pulling latest changes from " + repoUrl);
            git.pull()
               .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
               .call();
            System.out.println("Pulled latest changes.");
        } catch (Exception e) {
            System.err.println("Failed to pull repository: " + e.getMessage());
            // If it's a new repo and pull fails, it's critical.
            // If existing, maybe just network issue, but we want to ensure we have data.
            throw new IOException("Failed to pull repository", e);
        }
    }

    private void deleteDirectory(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    public byte[] getFileContent(String path) throws IOException {
        Path root = Path.of(localPath).normalize();
        Path filePath = root.resolve(path).normalize();
        if (!filePath.startsWith(root)) {
             throw new IOException("Invalid path: " + path);
        }
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new IOException("File not found: " + path);
        }
        return Files.readAllBytes(filePath);
    }

    public List<FileEntry> listFiles(String path) throws IOException {
        Path root = Path.of(localPath).normalize();
        Path dirPath = root.resolve(path).normalize();
        if (!dirPath.startsWith(root)) {
             throw new IOException("Invalid path: " + path);
        }
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
             throw new IOException("Directory not found: " + path);
        }

        List<FileEntry> entries = new ArrayList<>();
        try (var stream = Files.list(dirPath)) {
            stream.forEach(p -> {
                FileEntry entry = new FileEntry();
                entry.setName(p.getFileName().toString());
                entry.setPath(path.isEmpty() ? p.getFileName().toString() : path + "/" + p.getFileName().toString());
                entry.setType(Files.isDirectory(p) ? "dir" : "file");
                entry.setSize(tryGetSize(p));
                entries.add(entry);
            });
        }
        return entries;
    }

    private long tryGetSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    public static class FileEntry {
        private String name;
        private String path;
        private String type; // "file" or "dir"
        private long size;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }
}
