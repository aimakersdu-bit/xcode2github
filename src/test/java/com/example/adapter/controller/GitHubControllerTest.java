package com.example.adapter.controller;

import com.example.adapter.service.GitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.containsString;
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
                .andExpect(jsonPath("$.download_url").value(containsString("/raw/" + path)))
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    public void testGetRawContent() throws Exception {
        String path = "README.md";
        byte[] content = "Hello World".getBytes();
        given(gitService.getFileContent(path)).willReturn(content);

        mockMvc.perform(get("/repos/ah/futian/raw/" + path))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));
    }

    @Test
    public void testGetDirectoryContent() throws Exception {
        GitService.FileEntry entry = new GitService.FileEntry();
        entry.setName("src");
        entry.setPath("src");
        entry.setType("dir");
        entry.setSize(0);

        // For root path, the pattern match extraction results in empty string usually,
        // but let's test a sub-directory "src" to be safe and consistent with mock
        String path = "src";

        given(gitService.listFiles(path)).willReturn(Collections.singletonList(entry));

        mockMvc.perform(get("/repos/ah/futian/contents/" + path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("src"))
                .andExpect(jsonPath("$[0].type").value("dir"));
    }
}
