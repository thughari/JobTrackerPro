package com.thughari.jobtrackerpro.service.mock;

import com.thughari.jobtrackerpro.interfaces.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${app.base-url}")
    private String baseUrl;

    private final String UPLOAD_DIR = "uploads";

    public LocalStorageService() {
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) directory.mkdirs();
    }

    @Override
    public String uploadFile(MultipartFile file, String userId) {
        try {
            String fileName = userId + "-" + System.currentTimeMillis() + ".jpg";
            Path path = Paths.get(UPLOAD_DIR, fileName);
            Files.write(path, file.getBytes());
            
            return baseUrl + "/api/storage/files/" + fileName;
        } catch (Exception e) {
            throw new RuntimeException("Local upload failed", e);
        }
    }

    @Override
    public String uploadFromUrl(String externalUrl, String userId) {
        return externalUrl;
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            Path path = Paths.get(UPLOAD_DIR, fileName);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            System.err.println("Could not delete local file: " + e.getMessage());
        }
    }
}