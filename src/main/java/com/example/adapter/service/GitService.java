package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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

    @Value("${ezone.repo.ssl-verify:true}")
    private boolean sslVerify;

    private Git git;

    public void initRepo() throws IOException, GitAPIException, URISyntaxException {
        File repoDir = new File(localPath);
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            try {
                git = Git.open(repoDir);
                System.out.println("Opened existing repository.");

                configureSsl(git);

                // Pull changes
                git.pull()
                   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                   .call();
                System.out.println("Pulled latest changes.");
            } catch (Exception e) {
                // If opening fails, maybe it's corrupted, delete and re-clone
                System.err.println("Failed to open/pull repo, re-cloning: " + e.getMessage());
                deleteDirectory(repoDir);
                cloneRepo(repoDir);
            }
        } else {
            cloneRepo(repoDir);
        }
    }

    private void cloneRepo(File repoDir) throws GitAPIException, URISyntaxException, IOException {
        if (!repoDir.exists()) {
            repoDir.mkdirs();
        }
        System.out.println("Cloning repository from " + repoUrl);

        // Initialize empty repo
        git = Git.init().setDirectory(repoDir).call();

        // Configure SSL
        configureSsl(git);

        // Add remote
        git.remoteAdd()
                .setName("origin")
                .setUri(new URIish(repoUrl))
                .call();

        try {
            // Pull master from origin
            // We assume master since the user URL indicates it, and it's a safe default for now.
            // A more robust solution would fetch all and checkout HEAD, but JGit 'pull' is convenient.
            git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName("master")
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                    .call();
            System.out.println("Repository cloned (initialized and pulled).");
        } catch (GitAPIException e) {
            System.err.println("Failed to pull repository: " + e.getMessage());
            throw e;
        }
    }

    private void configureSsl(Git git) {
        git.getRepository().getConfig().setBoolean("http", null, "sslVerify", sslVerify);
        try {
            git.getRepository().getConfig().save();
        } catch (IOException e) {
            System.err.println("Failed to save git config: " + e.getMessage());
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
