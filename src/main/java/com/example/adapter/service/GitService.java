package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
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

    public synchronized void initRepo() throws IOException, GitAPIException {
        File repoDir = new File(localPath);
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            try {
                git = Git.open(repoDir);
                System.out.println("Opened existing repository.");
                // Fetch changes to update refs (not just pull, which merges)
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
        if (git == null) throw new IOException("Repository not initialized");
        Repository repository = git.getRepository();
        ObjectId commitId = resolveRef(ref);

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(path));

                if (!treeWalk.next()) {
                    throw new IOException("File not found: " + path + " in " + ref);
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                return loader.getBytes();
            }
        }
    }

    public List<FileEntry> listFiles(String ref, String path) throws IOException {
        if (git == null) throw new IOException("Repository not initialized");
        Repository repository = git.getRepository();
        ObjectId commitId = resolveRef(ref);

        List<FileEntry> entries = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(false);

                if (path != null && !path.isEmpty()) {
                     // We need to navigate to the path first
                     // But TreeWalk without recursive needs to manually find the tree
                     // Simpler way: filter by PathFilterGroup.createFromStrings(path) but that might be recursive

                     // Let's walk until we find the path
                    PathFilter filter = PathFilter.create(path);
                    // treeWalk.setFilter(filter); // This finds the ITEM at path.

                    // If path is "a/b", we want children of "a/b".
                    // The standard TreeWalk is top-down.

                    while (treeWalk.next()) {
                        if (filter.include(treeWalk)) {
                            if (treeWalk.isSubtree()) {
                                treeWalk.enterSubtree();
                                break; // found the dir, entered it, now iterate
                            } else {
                                // It's a file, not a dir, cannot list children
                                throw new IOException("Path is not a directory: " + path);
                            }
                        }
                        if (treeWalk.isSubtree()) {
                             // If path is "a/b", and we are at "a", we need to enter.
                             // But wait, PathFilter includes "a" if it is a prefix of "a/b"
                             // Let's rely on PathFilter.
                             // Actually, PathFilter.include returns true if the current entry is on the path to the target.
                             // But we need to be careful not to enter wrong subtrees.
                             // PathFilter handles this.
                             treeWalk.enterSubtree();
                        }
                    }

                    // If loop finished without breaking, we didn't find the path or it wasn't a subtree we could enter
                    // But wait, if we broke, we are now INSIDE the directory.
                    // However, we need to verify we are actually in the directory we wanted.
                    // This manual walking is error prone.

                    // Better approach for navigating to a subtree:
                    // Use TreeWalk.forPath, but that gives us the item itself.
                    // If it is a tree, we can create a new TreeWalk with that tree.

                    // Reset
                }
            }

            // Re-approach using separate logic for finding the tree
            ObjectId treeId = tree.getId();
            if (path != null && !path.isEmpty()) {
                 try (TreeWalk walk = TreeWalk.forPath(repository, path, tree)) {
                     if (walk == null) {
                         throw new IOException("Path not found: " + path);
                     }
                     if (!walk.isSubtree()) {
                         throw new IOException("Path is not a directory: " + path);
                     }
                     treeId = walk.getObjectId(0);
                 }
            }

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(treeId);
                treeWalk.setRecursive(false);
                while (treeWalk.next()) {
                    FileEntry entry = new FileEntry();
                    entry.setName(treeWalk.getNameString());
                    String entryPath = path != null && !path.isEmpty() ? path + "/" + treeWalk.getNameString() : treeWalk.getNameString();
                    entry.setPath(entryPath);
                    entry.setType(treeWalk.isSubtree() ? "dir" : "file");
                    // Getting size is expensive (requires loading object), set to 0 or try to get if easy
                    // ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    // entry.setSize(loader.getSize());
                    entry.setSize(0); // Optional for directories usually
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private ObjectId resolveRef(String ref) throws IOException {
        if (ref == null || ref.isEmpty()) ref = "master"; // Default to master
        ObjectId id = git.getRepository().resolve(ref);
        if (id == null) {
             // Try assuming it's a branch name that needs refs/heads/
             id = git.getRepository().resolve("refs/heads/" + ref);
        }
        if (id == null) {
             // Try assuming it's a tag
             id = git.getRepository().resolve("refs/tags/" + ref);
        }
        if (id == null) {
            throw new IOException("Ref not found: " + ref);
        }
        return id;
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
