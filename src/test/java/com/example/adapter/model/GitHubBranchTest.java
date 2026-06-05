package com.example.adapter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubBranchTest {

    @Test
    void branchGettersAndSetters() {
        GitHubBranch branch = new GitHubBranch();
        branch.setName("main");
        branch.setProtectedBranch(true);

        assertEquals("main", branch.getName());
        assertTrue(branch.isProtectedBranch());
    }

    @Test
    void commitGettersAndSetters() {
        GitHubBranch.Commit commit = new GitHubBranch.Commit();
        commit.setSha("abc123");
        commit.setUrl("http://example.com/commits/abc123");

        assertEquals("abc123", commit.getSha());
        assertEquals("http://example.com/commits/abc123", commit.getUrl());
    }

    @Test
    void branchWithCommit() {
        GitHubBranch branch = new GitHubBranch();
        GitHubBranch.Commit commit = new GitHubBranch.Commit();
        commit.setSha("def456");
        branch.setCommit(commit);

        assertNotNull(branch.getCommit());
        assertEquals("def456", branch.getCommit().getSha());
    }

    @Test
    void branchEquality() {
        GitHubBranch a = new GitHubBranch();
        a.setName("main");
        a.setProtectedBranch(false);

        GitHubBranch b = new GitHubBranch();
        b.setName("main");
        b.setProtectedBranch(false);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void branchToString_containsName() {
        GitHubBranch branch = new GitHubBranch();
        branch.setName("develop");
        assertTrue(branch.toString().contains("develop"));
    }
}
