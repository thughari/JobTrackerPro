package com.thughari.jobtrackerpro.service.mock;

import com.thughari.jobtrackerpro.interfaces.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    private static final long MAX_RESOURCE_FILE_SIZE = 10 * 1024 * 1024;

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
    public String uploadResourceFile(MultipartFile file, String userId) {
        try {
            if (file.getSize() > MAX_RESOURCE_FILE_SIZE) {
                throw new MaxUploadSizeExceededException(MAX_RESOURCE_FILE_SIZE);
            }

            String extension = getExtensionFromFilename(file.getOriginalFilename());
            if (!isAllowedResourceExtension(extension)) {
                throw new IllegalArgumentException("Invalid file type. Only PDF, DOC, DOCX and TXT are allowed.");
            }

            String fileName = "resources-" + userId + "-" + System.currentTimeMillis() + extension;
            Path path = Paths.get(UPLOAD_DIR, fileName);
            Files.write(path, file.getBytes());

            return baseUrl + "/api/storage/files/" + fileName;
        } catch (IllegalArgumentException | MaxUploadSizeExceededException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Local resource upload failed", e);
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

    private boolean isAllowedResourceExtension(String extension) {
        return ".pdf".equals(extension) || ".doc".equals(extension) || ".docx".equals(extension) || ".txt".equals(extension);
    }

    private String getExtensionFromFilename(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".pdf";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    }
}
