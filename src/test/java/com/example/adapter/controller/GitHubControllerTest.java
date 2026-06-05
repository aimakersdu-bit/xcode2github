package com.example.adapter.controller;

import com.example.adapter.service.GitService;
import com.example.adapter.util.GitHubContentFactory;
import com.example.adapter.util.UrlBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubController.class)
@Import({UrlBuilder.class, GitHubContentFactory.class})
public class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitService gitService;

    @Test
    public void testGetFileContent() throws Exception {
        String path = "README.md";
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

        String path = "src";

        given(gitService.listFiles(path)).willReturn(Collections.singletonList(entry));

        mockMvc.perform(get("/repos/ah/futian/contents/" + path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("src"))
                .andExpect(jsonPath("$[0].type").value("dir"));
    }
}
