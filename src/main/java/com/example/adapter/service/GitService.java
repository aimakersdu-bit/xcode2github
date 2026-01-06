package com.example.adapter.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    // Store last fetch time to throttle updates
    private long lastFetchTime = 0;
    private static final long FETCH_INTERVAL_MS = 60000; // 1 minute

    public void initRepo() throws IOException, GitAPIException {
        File repoDir = new File(localPath);
        if (repoDir.exists() && new File(repoDir, ".git").exists()) {
            try {
                git = Git.open(repoDir);
                repository = git.getRepository();
                System.out.println("Opened existing repository.");
                checkAndFetchUpdates();
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
        git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(repoDir)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                .call();
        repository = git.getRepository();
        System.out.println("Repository cloned.");
    }

    public void checkAndFetchUpdates() {
        if (System.currentTimeMillis() - lastFetchTime > FETCH_INTERVAL_MS) {
            try {
                git.fetch()
                   .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                   .call();
                lastFetchTime = System.currentTimeMillis();
                System.out.println("Fetched latest changes.");
            } catch (Exception e) {
                System.err.println("Error fetching updates: " + e.getMessage());
            }
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

    public List<String> getBranches() {
        List<String> branches = new ArrayList<>();
        if (git == null) return branches;
        try {
            List<Ref> refs = git.branchList().setListMode(ListMode.ALL).call();
            for (Ref ref : refs) {
                String name = ref.getName();
                if (name.startsWith("refs/heads/")) {
                    branches.add(name.substring("refs/heads/".length()));
                } else if (name.startsWith("refs/remotes/origin/")) {
                    String b = name.substring("refs/remotes/origin/".length());
                    if (!b.equals("HEAD")) {
                        branches.add(b);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return branches.stream().distinct().collect(Collectors.toList());
    }

    private ObjectId resolveRef(String ref) throws IOException {
        if (ref == null || ref.isEmpty()) {
            // Priority 1: HEAD
            ObjectId head = repository.resolve("HEAD");
            if (head != null) return head;
            // Priority 2: origin/master
            ObjectId remoteMaster = repository.resolve("refs/remotes/origin/master");
            if (remoteMaster != null) return remoteMaster;
            return repository.resolve("refs/heads/master");
        }

        // Priority 1: Exact
        ObjectId id = repository.resolve(ref);
        if (id != null) return id;

        // Priority 2: Remote branch (e.g. origin/feature)
        id = repository.resolve("refs/remotes/origin/" + ref);
        if (id != null) return id;

        // Priority 3: Local branch (e.g. refs/heads/feature)
        id = repository.resolve("refs/heads/" + ref);
        return id;
    }

    public byte[] getFileContent(String path, String refName) throws IOException {
        if (repository == null) throw new IOException("Repository not initialized");

        ObjectId commitId = resolveRef(refName);
        if (commitId == null) throw new IOException("Ref not found: " + (refName == null ? "HEAD" : refName));

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(path));

                if (!treeWalk.next()) {
                    throw new IOException("File not found: " + path + " in " + commitId.name());
                }

                // Ensure it is a file
                if (treeWalk.isSubtree()) {
                    throw new IOException("Path is a directory: " + path);
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
        if (commitId == null) throw new IOException("Ref not found: " + (refName == null ? "HEAD" : refName));

        List<FileEntry> result = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(false);

                if (path != null && !path.isEmpty()) {
                    PathFilter f = PathFilter.create(path);
                    treeWalk.setFilter(f);

                    boolean found = false;
                    while (treeWalk.next()) {
                        // The filter is active. It will allow walking into parents of the target path.
                        // When we hit the target path, we need to decide what to do.

                        String currentPath = treeWalk.getPathString();

                        if (path.equals(currentPath)) {
                            found = true;
                            if (treeWalk.isSubtree()) {
                                treeWalk.enterSubtree();
                                break; // Break to list children of subtree
                            } else {
                                // It is a file. We return empty list to indicate it's not a directory.
                                // The controller should try getFileContent.
                                return new ArrayList<>();
                            }
                        }

                        if (treeWalk.isSubtree()) {
                            // If the current path is a prefix of 'path', we MUST enter.
                            // PathFilter generally handles "should we include this", but with recursive=false,
                            // we must manually traverse directories.
                            // BUT: PathFilter.include() returns true if the path is a parent of the filter path.
                            // So if include() is true, and it is a subtree, we should enter?
                            // PathFilter logic is complex when manual iteration is involved.

                            // Let's do manual check:
                            if (path.startsWith(currentPath + "/")) {
                                treeWalk.enterSubtree();
                            }
                        }
                    }

                    if (!found) {
                        // Check if we ended up at the file itself?
                         // If path points to a file, this logic might just stop.
                         // But listFiles is for directory listing.
                         // If it's a file, we should maybe return just that file?
                         // Let's assume listFiles is only called when we expect a dir.
                         // If the path was a file, the loop above might consume it and exit?
                         // If treeWalk.getPathString().equals(path), and !isSubtree, it's a file.
                         return result;
                    }
                }

                // Collect children
                while (treeWalk.next()) {
                     // If we used a filter, we might need to be careful?
                     // Once we entered the subtree, we are iterating its children.
                     // Does the filter apply to children?
                     // PathFilter: "matches the specified path, and its children"
                     // So yes, children match.

                     // We just add them.
                     result.add(createEntry(treeWalk));
                }
            }
        }
        return result;
    }

    private FileEntry createEntry(TreeWalk treeWalk) {
        FileEntry entry = new FileEntry();
        entry.setName(treeWalk.getNameString());
        entry.setPath(treeWalk.getPathString());
        entry.setType(treeWalk.isSubtree() ? "dir" : "file");
        entry.setSha(treeWalk.getObjectId(0).name());

        try {
            if (!treeWalk.isSubtree()) {
                 ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                 entry.setSize(loader.getSize());
            } else {
                entry.setSize(0);
            }
        } catch (Exception e) {
            entry.setSize(0);
        }
        return entry;
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
