package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            try {
                git = Git.open(repoDir);
                System.out.println("Opened existing repository.");
                try {
                    git.pull()
                       .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                       .call();
                    System.out.println("Pulled latest changes.");
                } catch (Exception e) {
                     System.err.println("Pull failed (could be offline or auth error): " + e.getMessage());
                     // We continue, using what we have.
                }
            } catch (Exception e) {
                System.err.println("Failed to open repo: " + e.getMessage());
                // Consider re-clone logic if totally corrupt
            }
        } else {
            cloneRepo(repoDir);
        }
    }

    private void cloneRepo(File repoDir) throws GitAPIException {
        if (!repoDir.exists()) {
            repoDir.mkdirs();
        }
        System.out.println("Cloning repository from " + repoUrl);
        try {
            git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                    .call();
            System.out.println("Repository cloned.");
        } catch (GitAPIException e) {
            System.err.println("Failed to clone repository: " + e.getMessage());
            throw e;
        }
    }

    // New method to resolve ref
    private ObjectId resolveRef(String refName) throws IOException {
        Repository repository = git.getRepository();
        ObjectId objectId = repository.resolve(refName);
        if (objectId == null) {
            // Try origin/refName if not found locally
             objectId = repository.resolve("origin/" + refName);
        }
        if (objectId == null) {
            throw new IOException("Ref not found: " + refName);
        }
        return objectId;
    }

    public byte[] getFileContent(String path, String refName) throws IOException {
        Repository repository = git.getRepository();
        ObjectId commitId = resolveRef(refName);

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(path));

                if (!treeWalk.next()) {
                    throw new IOException("File not found: " + path + " in " + refName);
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                return loader.getBytes();
            }
        }
    }

    public List<FileEntry> listFiles(String path, String refName) throws IOException {
        Repository repository = git.getRepository();
        ObjectId commitId = resolveRef(refName);

        List<FileEntry> entries = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            if (path != null && !path.isEmpty()) {
                 // If path provided, find the subtree and reset the walk to that subtree
                 // TreeWalk.forPath returns a new TreeWalk positioned at the path
                 try (TreeWalk subWalk = TreeWalk.forPath(repository, path, tree)) {
                     if (subWalk == null) {
                         throw new IOException("Path not found: " + path);
                     }
                     if (!subWalk.isSubtree()) {
                         throw new IOException("Path is not a directory: " + path);
                     }
                     subWalk.enterSubtree();

                     // Iterate using subWalk
                     while (subWalk.next()) {
                         processEntry(subWalk, path, entries, repository);
                     }
                 }
             } else {
                 try (TreeWalk treeWalk = new TreeWalk(repository)) {
                     treeWalk.addTree(tree);
                     treeWalk.setRecursive(false);
                     while (treeWalk.next()) {
                         processEntry(treeWalk, path, entries, repository);
                     }
                 }
             }
        }
        return entries;
    }

    private void processEntry(TreeWalk treeWalk, String path, List<FileEntry> entries, Repository repository) throws IOException {
        FileEntry entry = new FileEntry();
        entry.setName(treeWalk.getNameString());
        entry.setPath(path != null && !path.isEmpty() ? path + "/" + treeWalk.getNameString() : treeWalk.getNameString());
        entry.setType(treeWalk.isSubtree() ? "dir" : "file");

        // Getting size is expensive for tree, cheap for blob
        // For now mock size or load it if file
        if (!treeWalk.isSubtree()) {
            ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
            entry.setSize(loader.getSize());
        } else {
            entry.setSize(0);
        }

        entries.add(entry);
    }

    // Fallback for existing calls if any
    public byte[] getFileContent(String path) throws IOException {
        return getFileContent(path, "HEAD");
    }

    public List<FileEntry> listFiles(String path) throws IOException {
        return listFiles(path, "HEAD");
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
