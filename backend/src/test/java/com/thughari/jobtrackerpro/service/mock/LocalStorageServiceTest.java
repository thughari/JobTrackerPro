package com.thughari.jobtrackerpro.service.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadFileWritesToDiskAndReturnsUrl() {
        System.setProperty("user.dir", tempDir.toString());
        LocalStorageService service = new LocalStorageService();
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "abc".getBytes());

        String url = service.uploadFile(file, "user1");

        assertTrue(url.startsWith("http://localhost:8080/api/storage/files/user1-"));
    }

    @Test
    void uploadResourceFileRejectsUnsupportedExtension() {
        LocalStorageService service = new LocalStorageService();
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");

        MockMultipartFile file = new MockMultipartFile("file", "bad.exe", "application/octet-stream", "abc".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.uploadResourceFile(file, "user1"));
    }
}
