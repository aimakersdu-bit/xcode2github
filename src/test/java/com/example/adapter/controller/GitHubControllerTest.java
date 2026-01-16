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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                .andExpect(jsonPath("$._links.self").exists());
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

    @Test
    public void testDownloadUrlIsRaw() throws Exception {
        String path = "README.md";
        String content = "Hello World";

        given(gitService.listFiles(path)).willThrow(new IOException("Not a directory"));
        given(gitService.getFileContent(path)).willReturn(content.getBytes());

        // 1. Get content metadata
        var result = mockMvc.perform(get("/repos/ah/futian/contents/" + path))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String search = "\"download_url\":\"";
        int start = responseBody.indexOf(search);
        assertTrue(start > 0, "download_url not found in response");
        start += search.length();
        int end = responseBody.indexOf("\"", start);
        String downloadUrl = responseBody.substring(start, end);

        // 2. Fetch from download_url
        String relativeUrl = downloadUrl.replace("http://localhost:8080", "");
        var downloadResult = mockMvc.perform(get(relativeUrl))
                .andExpect(status().isOk())
                .andReturn();

        String downloadContent = downloadResult.getResponse().getContentAsString();

        // 3. Assert content is raw
        assertEquals(content, downloadContent);
        assertFalse(downloadContent.trim().startsWith("{"));
    }
}
