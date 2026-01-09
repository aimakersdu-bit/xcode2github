package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
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
                // Pull changes
                git.pull()
                   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                   .call();
                System.out.println("Pulled latest changes.");
            } catch (Exception e) {
                System.err.println("Failed to open/pull repo, re-cloning: " + e.getMessage());
                deleteDirectory(repoDir);
                cloneRepo(repoDir);
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

    private void deleteDirectory(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    public byte[] getFileContent(String ref, String path) throws IOException {
        if (git == null) {
            throw new IllegalStateException("Repository not initialized");
        }
        Repository repository = git.getRepository();
        ObjectId commitId = resolveRef(repository, ref);
        if (commitId == null) {
            throw new IllegalArgumentException("Reference not found: " + ref);
        }

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(path));
                if (!treeWalk.next()) {
                    throw new FileNotFoundException("File not found in " + ref + ": " + path);
                }
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                return loader.getBytes();
            }
        }
    }

    // Deprecated: Uses local filesystem, kept for backward compatibility if needed, but updated to delegate
    public byte[] getFileContent(String path) throws IOException {
        return getFileContent("master", path);
    }

    public List<FileEntry> listFiles(String ref, String path) throws IOException {
        if (git == null) throw new IllegalStateException("Repo not init");
        Repository repository = git.getRepository();
        ObjectId commitId = resolveRef(repository, ref);
        if (commitId == null) {
             // Fallback or error?
             throw new IllegalArgumentException("Reference not found: " + ref);
        }

        List<FileEntry> entries = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            if (path == null || path.isEmpty()) {
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(false);
                    while (treeWalk.next()) {
                        entries.add(createFileEntry(treeWalk));
                    }
                }
            } else {
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree)) {
                    if (treeWalk == null) {
                        throw new FileNotFoundException("Path not found: " + path);
                    }
                    if (treeWalk.isSubtree()) {
                        treeWalk.enterSubtree();
                        while (treeWalk.next()) {
                            entries.add(createFileEntry(treeWalk));
                        }
                    } else {
                         throw new IOException("Path is not a directory: " + path);
                    }
                }
            }
        }
        return entries;
    }

    // Deprecated: Uses filesystem
    public List<FileEntry> listFiles(String path) throws IOException {
        return listFiles("master", path);
    }

    private ObjectId resolveRef(Repository repo, String ref) throws IOException {
        ObjectId oid = repo.resolve(ref);
        if (oid == null && !ref.startsWith("origin/")) {
            // Try origin/ref
            oid = repo.resolve("origin/" + ref);
        }
        return oid;
    }

    private FileEntry createFileEntry(TreeWalk treeWalk) {
        FileEntry entry = new FileEntry();
        entry.setName(treeWalk.getNameString());
        entry.setPath(treeWalk.getPathString());
        entry.setType(treeWalk.isSubtree() ? "dir" : "file");
        if (!treeWalk.isSubtree()) {
             try {
                 long size = treeWalk.getObjectReader().getObjectSize(treeWalk.getObjectId(0), Constants.OBJ_BLOB);
                 entry.setSize(size);
             } catch (Exception e) {
                 entry.setSize(0);
             }
        } else {
            entry.setSize(0);
        }
        return entry;
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
