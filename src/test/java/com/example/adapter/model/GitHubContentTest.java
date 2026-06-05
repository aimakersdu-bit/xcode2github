package com.example.adapter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubContentTest {

    @Test
    void contentGettersAndSetters() {
        GitHubContent content = new GitHubContent();
        content.setName("README.md");
        content.setPath("docs/README.md");
        content.setSha("sha123");
        content.setSize(100);
        content.setUrl("http://example.com/contents/README.md");
        content.setHtml_url("http://example.com/blob/README.md");
        content.setGit_url("http://example.com/git/sha123");
        content.setDownload_url("http://example.com/raw/README.md");
        content.setType("file");
        content.setContent("SGVsbG8=");
        content.setEncoding("base64");

        assertEquals("README.md", content.getName());
        assertEquals("docs/README.md", content.getPath());
        assertEquals("sha123", content.getSha());
        assertEquals(100, content.getSize());
        assertEquals("http://example.com/contents/README.md", content.getUrl());
        assertEquals("http://example.com/blob/README.md", content.getHtml_url());
        assertEquals("http://example.com/git/sha123", content.getGit_url());
        assertEquals("http://example.com/raw/README.md", content.getDownload_url());
        assertEquals("file", content.getType());
        assertEquals("SGVsbG8=", content.getContent());
        assertEquals("base64", content.getEncoding());
    }

    @Test
    void linksGettersAndSetters() {
        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf("http://example.com/self");
        links.setGit("http://example.com/git");
        links.setHtml("http://example.com/html");

        assertEquals("http://example.com/self", links.getSelf());
        assertEquals("http://example.com/git", links.getGit());
        assertEquals("http://example.com/html", links.getHtml());
    }

    @Test
    void contentWithLinks() {
        GitHubContent content = new GitHubContent();
        GitHubContent.Links links = new GitHubContent.Links();
        links.setSelf("http://self");
        content.set_links(links);

        assertNotNull(content.get_links());
        assertEquals("http://self", content.get_links().getSelf());
    }

    @Test
    void contentEquality() {
        GitHubContent a = new GitHubContent();
        a.setName("file.txt");
        a.setType("file");

        GitHubContent b = new GitHubContent();
        b.setName("file.txt");
        b.setType("file");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void contentToString_containsName() {
        GitHubContent content = new GitHubContent();
        content.setName("index.html");
        assertTrue(content.toString().contains("index.html"));
    }

    @Test
    void dirTypeContent() {
        GitHubContent content = new GitHubContent();
        content.setType("dir");
        content.setName("src");
        content.setSize(0);

        assertEquals("dir", content.getType());
        assertEquals(0, content.getSize());
    }
}
