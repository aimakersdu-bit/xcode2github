package com.example.adapter.controller;

import com.example.adapter.service.GitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GitHubController.class)
class GitHubControllerExtraTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitService gitService;

    // ---- /user endpoint ----

    @Test
    void getUser_returnsMockLogin() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("mock-user"));
    }

    // ---- /repos/{owner}/{repo} endpoint ----

    @Test
    void getRepo_returnsDefaultBranch() throws Exception {
        mockMvc.perform(get("/repos/owner/repo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.default_branch").value("master"));
    }

    // ---- /repos/{owner}/{repo}/branches endpoint ----

    @Test
    void getBranches_returnsMasterBranch() throws Exception {
        mockMvc.perform(get("/repos/owner/repo/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("master"))
                .andExpect(jsonPath("$[0].commit.sha").value("mock-sha"))
                .andExpect(jsonPath("$[0].commit.url").exists());
    }

    // ---- error paths for /contents ----

    @Test
    void getContents_returns404WhenFileNotFound() throws Exception {
        given(gitService.listFiles("missing.txt")).willThrow(new IOException("Directory not found"));
        given(gitService.getFileContent("missing.txt")).willThrow(new IOException("File not found"));

        mockMvc.perform(get("/repos/owner/repo/contents/missing.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not Found"));
    }

    @Test
    void getContents_returns500OnUnexpectedError() throws Exception {
        given(gitService.listFiles("bad")).willThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/repos/owner/repo/contents/bad"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("boom"));
    }

    @Test
    void getContents_fileResponseIncludesBase64Content() throws Exception {
        String data = "package main;";
        given(gitService.listFiles("Main.java")).willThrow(new IOException("Not a directory"));
        given(gitService.getFileContent("Main.java")).willReturn(data.getBytes());

        mockMvc.perform(get("/repos/owner/repo/contents/Main.java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encoding").value("base64"))
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.size").value(data.length()));
    }

    @Test
    void getContents_directoryFiltersOutDotGit() throws Exception {
        GitService.FileEntry gitEntry = new GitService.FileEntry();
        gitEntry.setName(".git");
        gitEntry.setPath(".git");
        gitEntry.setType("dir");
        gitEntry.setSize(0);

        GitService.FileEntry srcEntry = new GitService.FileEntry();
        srcEntry.setName("src");
        srcEntry.setPath("src");
        srcEntry.setType("dir");
        srcEntry.setSize(0);

        given(gitService.listFiles("root")).willReturn(java.util.List.of(gitEntry, srcEntry));

        mockMvc.perform(get("/repos/owner/repo/contents/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("src"));
    }
}
