package com.example.adapter.controller;

import com.example.adapter.service.GitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubController.class)
public class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitService gitService;

    @Test
    public void testGetFileContent() throws Exception {
        String path = "README.md";
        // listFiles should throw IOException to indicate it's not a directory
        given(gitService.listFiles(path)).willThrow(new IOException("Not a directory"));
        given(gitService.getFileContent(path)).willReturn("Hello World".getBytes());

        mockMvc.perform(get("/repos/ah/futian/contents/" + path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("README.md"))
                .andExpect(jsonPath("$.type").value("file"))
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.download_url").value(containsString("/repos/ah/futian/raw/README.md")))
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    public void testGetDirectoryContent() throws Exception {
        GitService.FileEntry dirEntry = new GitService.FileEntry();
        dirEntry.setName("sub");
        dirEntry.setPath("src/sub");
        dirEntry.setType("dir");
        dirEntry.setSize(0);

        GitService.FileEntry fileEntry = new GitService.FileEntry();
        fileEntry.setName("Main.java");
        fileEntry.setPath("src/Main.java");
        fileEntry.setType("file");
        fileEntry.setSize(100);

        String path = "src";

        given(gitService.listFiles(path)).willReturn(List.of(dirEntry, fileEntry));

        mockMvc.perform(get("/repos/ah/futian/contents/" + path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("sub"))
                .andExpect(jsonPath("$[0].type").value("dir"))
                .andExpect(jsonPath("$[0].download_url").value(nullValue()))
                .andExpect(jsonPath("$[1].name").value("Main.java"))
                .andExpect(jsonPath("$[1].type").value("file"))
                .andExpect(jsonPath("$[1].download_url").value(containsString("/repos/ah/futian/raw/src/Main.java")));
    }

    @Test
    public void testGetRawContent() throws Exception {
        String path = "README.md";
        byte[] content = "Hello Raw World".getBytes();
        given(gitService.getFileContent(path)).willReturn(content);

        mockMvc.perform(get("/repos/ah/futian/raw/" + path))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().bytes(content));
    }
}
