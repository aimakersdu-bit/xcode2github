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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

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
                // Verify download_url structure matches /raw/{ref}/{path}
                .andExpect(jsonPath("$.download_url").value(containsString("/repos/ah/futian/raw/master/" + path)))
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    public void testGetDirectoryContent() throws Exception {
        GitService.FileEntry entry = new GitService.FileEntry();
        entry.setName("src");
        entry.setPath("src");
        entry.setType("dir");
        entry.setSize(0);

        String path = "src";

        given(gitService.listFiles(path)).willReturn(Collections.singletonList(entry));

        mockMvc.perform(get("/repos/ah/futian/contents/" + path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("src"))
                .andExpect(jsonPath("$[0].type").value("dir"));
    }

    @Test
    public void testRawContentEndpoint() throws Exception {
        String path = "src/main/java/Main.java";
        byte[] content = "public class Main {}".getBytes();

        given(gitService.getFileContent(path)).willReturn(content);

        // Test with ref 'master'
        mockMvc.perform(get("/repos/ah/futian/raw/master/" + path))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));
    }
}
