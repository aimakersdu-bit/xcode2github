package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

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
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            try {
                git = Git.open(repoDir);
                log.info("Opened existing repository at {}", localPath);
                git.pull()
                   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                   .call();
                log.info("Pulled latest changes.");
            } catch (Exception e) {
                log.warn("Failed to open/pull repo, re-cloning: {}", e.getMessage(), e);
                deleteDirectory(repoDir);
                cloneRepo(repoDir);
            }
        } else {
            cloneRepo(repoDir);
        }
    }

    private void cloneRepo(File repoDir) throws GitAPIException, IOException {
        if (!repoDir.exists() && !repoDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + repoDir.getAbsolutePath());
        }
        log.info("Cloning repository from {}", repoUrl);
        try {
            git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                    .call();
            log.info("Repository cloned successfully.");
        } catch (InvalidRemoteException e) {
            log.error("Invalid remote repository URL '{}': {}", repoUrl, e.getMessage());
            throw e;
        } catch (TransportException e) {
            log.error("Transport error (check credentials/network) for '{}': {}", repoUrl, e.getMessage());
            throw e;
        } catch (GitAPIException e) {
            log.error("Failed to clone repository: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void deleteDirectory(File file) throws IOException {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete: " + file.getAbsolutePath());
        }
    }

    public byte[] getFileContent(String path) throws IOException {
        Path root = Path.of(localPath).normalize();
        Path filePath = root.resolve(path).normalize();
        if (!filePath.startsWith(root)) {
            throw new IOException("Invalid path: " + path);
        }
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + path);
        }
        if (Files.isDirectory(filePath)) {
            throw new IOException("Path is a directory, not a file: " + path);
        }
        return Files.readAllBytes(filePath);
    }

    public boolean isDirectory(String path) {
        Path root = Path.of(localPath).normalize();
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            return false;
        }
        return Files.isDirectory(resolved);
    }

    public List<FileEntry> listFiles(String path) throws IOException {
        Path root = Path.of(localPath).normalize();
        Path dirPath = root.resolve(path).normalize();
        if (!dirPath.startsWith(root)) {
            throw new IOException("Invalid path: " + path);
        }
        if (!Files.exists(dirPath)) {
            throw new IOException("Directory not found: " + path);
        }
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Path is not a directory: " + path);
        }

        List<FileEntry> entries = new ArrayList<>();
        try (var stream = Files.list(dirPath)) {
            stream.forEach(p -> {
                FileEntry entry = new FileEntry();
                entry.setName(p.getFileName().toString());
                entry.setPath(path.isEmpty() ? p.getFileName().toString() : path + "/" + p.getFileName().toString());
                entry.setType(Files.isDirectory(p) ? "dir" : "file");
                entry.setSize(getFileSize(p));
                entries.add(entry);
            });
        }
        return entries;
    }

    private long getFileSize(Path p) {
        try {
            if (Files.isDirectory(p)) {
                return 0;
            }
            return Files.size(p);
        } catch (IOException e) {
            log.warn("Could not read size for {}: {}", p, e.getMessage());
            return -1;
        }
    }

    public static class FileEntry {
        private String name;
        private String path;
        private String type;
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
