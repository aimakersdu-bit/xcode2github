package com.example.adapter.controller;

import com.example.adapter.service.GitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubController.class)
public class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitService gitService;

    @Test
    public void testGetContents_Directory() throws Exception {
        GitService.FileEntry entry = new GitService.FileEntry();
        entry.setName("template");
        entry.setPath("src/main/java/com/ezone/devops/ezcode/template");
        entry.setType("dir");
        entry.setSize(0);

        when(gitService.getFileType("src/main/java/com/ezone/devops/ezcode/template")).thenReturn(GitService.FileType.DIRECTORY);
        when(gitService.listFiles("src/main/java/com/ezone/devops/ezcode/template")).thenReturn(List.of(entry));

        mockMvc.perform(get("/repos/ah/futian/contents/src/main/java/com/ezone/devops/ezcode/template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("template"))
                .andExpect(jsonPath("$[0].type").value("dir"));
    }

    @Test
    public void testGetContents_File() throws Exception {
        String filePath = "src/main/java/Test.java";
        String content = "public class Test {}";
        byte[] contentBytes = content.getBytes();

        when(gitService.getFileType(filePath)).thenReturn(GitService.FileType.FILE);
        when(gitService.getFileContent(filePath)).thenReturn(contentBytes);

        mockMvc.perform(get("/repos/ah/futian/contents/" + filePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test.java"))
                .andExpect(jsonPath("$.type").value("file"))
                .andExpect(jsonPath("$.content").exists());
    }
}
