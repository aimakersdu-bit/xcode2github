package com.example.adapter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    private GitService gitService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        gitService = new GitService();
        setField(gitService, "localPath", tempDir.toString());
    }

    /** Inject a value into a private field via reflection. */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ---- getFileContent tests ----

    @Test
    void getFileContent_returnsBytes() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello World");

        byte[] content = gitService.getFileContent("hello.txt");
        assertEquals("Hello World", new String(content));
    }

    @Test
    void getFileContent_nestedPath() throws IOException {
        Path subdir = tempDir.resolve("sub");
        Files.createDirectories(subdir);
        Path file = subdir.resolve("data.txt");
        Files.writeString(file, "nested");

        byte[] content = gitService.getFileContent("sub/data.txt");
        assertEquals("nested", new String(content));
    }

    @Test
    void getFileContent_throwsOnMissingFile() {
        IOException ex = assertThrows(IOException.class,
                () -> gitService.getFileContent("nonexistent.txt"));
        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    void getFileContent_throwsOnDirectory() throws IOException {
        Path dir = tempDir.resolve("adir");
        Files.createDirectories(dir);

        IOException ex = assertThrows(IOException.class,
                () -> gitService.getFileContent("adir"));
        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    void getFileContent_throwsOnPathTraversal() {
        IOException ex = assertThrows(IOException.class,
                () -> gitService.getFileContent("../etc/passwd"));
        assertTrue(ex.getMessage().contains("Invalid path"));
    }

    // ---- listFiles tests ----

    @Test
    void listFiles_returnsEntries() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.createDirectories(tempDir.resolve("subdir"));

        List<GitService.FileEntry> entries = gitService.listFiles("");
        assertEquals(2, entries.size());

        GitService.FileEntry fileEntry = entries.stream()
                .filter(e -> e.getName().equals("a.txt")).findFirst().orElseThrow();
        assertEquals("file", fileEntry.getType());
        assertEquals("a.txt", fileEntry.getPath());
        assertEquals(1, fileEntry.getSize());

        GitService.FileEntry dirEntry = entries.stream()
                .filter(e -> e.getName().equals("subdir")).findFirst().orElseThrow();
        assertEquals("dir", dirEntry.getType());
        assertEquals("subdir", dirEntry.getPath());
    }

    @Test
    void listFiles_subDirectory() throws IOException {
        Path sub = tempDir.resolve("parent");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("child.txt"), "c");

        List<GitService.FileEntry> entries = gitService.listFiles("parent");
        assertEquals(1, entries.size());
        assertEquals("child.txt", entries.get(0).getName());
        assertEquals("parent/child.txt", entries.get(0).getPath());
    }

    @Test
    void listFiles_throwsOnMissingDirectory() {
        IOException ex = assertThrows(IOException.class,
                () -> gitService.listFiles("nope"));
        assertTrue(ex.getMessage().contains("Directory not found"));
    }

    @Test
    void listFiles_throwsOnFileInsteadOfDirectory() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "x");

        IOException ex = assertThrows(IOException.class,
                () -> gitService.listFiles("file.txt"));
        assertTrue(ex.getMessage().contains("Directory not found"));
    }

    @Test
    void listFiles_throwsOnPathTraversal() {
        IOException ex = assertThrows(IOException.class,
                () -> gitService.listFiles("../../etc"));
        assertTrue(ex.getMessage().contains("Invalid path"));
    }

    // ---- FileEntry tests ----

    @Test
    void fileEntry_gettersAndSetters() {
        GitService.FileEntry entry = new GitService.FileEntry();
        entry.setName("test.txt");
        entry.setPath("dir/test.txt");
        entry.setType("file");
        entry.setSize(42);

        assertEquals("test.txt", entry.getName());
        assertEquals("dir/test.txt", entry.getPath());
        assertEquals("file", entry.getType());
        assertEquals(42, entry.getSize());
    }

    // ---- deleteDirectory tests (via reflection) ----

    @Test
    void deleteDirectory_removesNestedStructure() throws Exception {
        Path dir = tempDir.resolve("toDelete");
        Files.createDirectories(dir.resolve("inner"));
        Files.writeString(dir.resolve("inner/f.txt"), "data");
        Files.writeString(dir.resolve("root.txt"), "root");

        assertTrue(Files.exists(dir));

        // Invoke private deleteDirectory via reflection
        java.lang.reflect.Method method = GitService.class.getDeclaredMethod("deleteDirectory", File.class);
        method.setAccessible(true);
        method.invoke(gitService, dir.toFile());

        assertFalse(Files.exists(dir));
    }

    // ---- getFileContent with empty content ----

    @Test
    void getFileContent_emptyFile() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        byte[] content = gitService.getFileContent("empty.txt");
        assertEquals(0, content.length);
    }
}
