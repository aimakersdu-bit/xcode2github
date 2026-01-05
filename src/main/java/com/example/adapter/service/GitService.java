package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
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
    private Repository repository;

    public void initRepo() throws IOException, GitAPIException {
        File repoDir = new File(localPath);
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            try {
                git = Git.open(repoDir);
                repository = git.getRepository();
                System.out.println("Opened existing repository.");
                // Fetch changes to ensure we have latest refs
                git.fetch()
                   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                   .call();
                System.out.println("Fetched latest changes.");
            } catch (Exception e) {
                System.err.println("Failed to open/fetch repo, re-cloning: " + e.getMessage());
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
                    .setCloneAllBranches(true)
                    .call();
            repository = git.getRepository();
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

    public byte[] getFileContent(String path, String refName) throws IOException {
        if (repository == null) throw new IOException("Repository not initialized");

        ObjectId commitId = resolveRef(refName);

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree)) {
                if (treeWalk == null) {
                    throw new IOException("File not found: " + path + " in " + refName);
                }
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                loader.copyTo(out);
                return out.toByteArray();
            }
        }
    }

    public List<FileEntry> listFiles(String path, String refName) throws IOException {
        if (repository == null) throw new IOException("Repository not initialized");

        ObjectId commitId = resolveRef(refName);
        List<FileEntry> entries = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            ObjectId treeId;
            if (path == null || path.isEmpty()) {
                treeId = tree.getId();
            } else {
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree)) {
                    if (treeWalk == null) {
                        throw new IOException("Path not found: " + path);
                    }
                    treeId = treeWalk.getObjectId(0);
                    // Check if it is a tree (directory)
                    if (treeWalk.getFileMode(0) == FileMode.MISSING) {
                         throw new IOException("Path not found: " + path);
                    }
                    if ((treeWalk.getFileMode(0).getBits() & FileMode.TYPE_MASK) != FileMode.TYPE_TREE) {
                         // It's a file, not a directory
                         throw new IOException("Path is not a directory: " + path);
                    }
                }
            }

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(treeId);
                treeWalk.setRecursive(false);
                while (treeWalk.next()) {
                    FileEntry entry = new FileEntry();
                    entry.setName(treeWalk.getNameString());
                    entry.setPath(path != null && !path.isEmpty() ? path + "/" + treeWalk.getNameString() : treeWalk.getNameString());
                    entry.setType(treeWalk.isSubtree() ? "dir" : "file");
                    entry.setSha(treeWalk.getObjectId(0).name());

                    if (!treeWalk.isSubtree()) {
                        try {
                            ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                            entry.setSize(loader.getSize());
                        } catch (Exception e) {
                            entry.setSize(0);
                        }
                    }
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private ObjectId resolveRef(String refName) throws IOException {
        if (refName == null || refName.isEmpty()) {
            refName = "master"; // Default
        }
        // Try exact match, then refs/heads/, then refs/tags/
        ObjectId id = repository.resolve(refName);
        if (id == null) {
            id = repository.resolve("refs/heads/" + refName);
        }
        if (id == null) {
            id = repository.resolve("refs/tags/" + refName);
        }
        if (id == null) {
            // Also try origin/refName if we fetched
             id = repository.resolve("refs/remotes/origin/" + refName);
        }
        if (id == null) {
            throw new IOException("Ref not found: " + refName);
        }
        return id;
    }

    public List<String> listBranches() throws IOException, GitAPIException {
        if (git == null) throw new IOException("Repository not initialized");
        List<Ref> branches = git.branchList().call();
        // Also list remote branches?
        // git.branchList().setListMode(ListMode.ALL).call();
        // But for now local branches should mirror remote if we clone/fetch correctly.

        List<String> names = new ArrayList<>();
        for (Ref branch : branches) {
            names.add(branch.getName().replace("refs/heads/", ""));
        }
        return names;
    }

    public static class FileEntry {
        private String name;
        private String path;
        private String type; // "file" or "dir"
        private long size;
        private String sha;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public String getSha() { return sha; }
        public void setSha(String sha) { this.sha = sha; }
    }
}
