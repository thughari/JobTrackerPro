package com.thughari.jobtrackerpro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.thughari.jobtrackerpro.exception.InvalidImageException;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.interfaces.StorageService;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.storage.type", havingValue = "r2")
public class CloudStorageService implements StorageService {

    private final S3Client s3Client;

    private static final long MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_RESOURCE_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Avatar Bucket Config
    @Value("${cloudflare.r2.bucket.avatars}")
    private String avatarBucket;

    @Value("${cloudflare.r2.public-url.avatars}")
    private String avatarPublicUrl;

    // Resource Bucket Config
    @Value("${cloudflare.r2.bucket.resources}")
    private String resourceBucket;

    @Value("${cloudflare.r2.public-url.resources}")
    private String resourcePublicUrl;

    public CloudStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Upload User Profile Avatar
     */
    @Override
    public String uploadFile(MultipartFile file, String userId) {
        String contentType = file.getContentType();
        if (!isValidImageContent(contentType)) {
            throw new InvalidImageException("Invalid file type. Only JPG, PNG, GIF, WEBP are allowed.");
        }

        if (file.getSize() > MAX_IMAGE_FILE_SIZE) {
            throw new MaxUploadSizeExceededException(MAX_IMAGE_FILE_SIZE);
        }

        try {
            String extension = getExtensionFromContentType(contentType);
            String fileName = userId + "-" + System.currentTimeMillis() + extension;

            uploadToS3(avatarBucket, fileName, contentType, file);

            return avatarPublicUrl + "/" + fileName;
        } catch (Exception e) {
            log.error("Failed to upload avatar to R2: {}", e.getMessage());
            throw new RuntimeException("Failed to upload avatar to cloud storage", e);
        }
    }

    /**
     * Upload Career Resource (PDF, DOCX, etc.)
     */
    @Override
    public String uploadResourceFile(MultipartFile file, String userId) {
        String contentType = file.getContentType();
        if (!isValidResourceFileType(contentType, file.getOriginalFilename())) {
            throw new IllegalArgumentException("Invalid file type. Only PDF, DOC, DOCX and TXT are allowed.");
        }

        if (file.getSize() > MAX_RESOURCE_FILE_SIZE) {
            throw new MaxUploadSizeExceededException(MAX_RESOURCE_FILE_SIZE);
        }

        try {
            String extension = getExtensionFromFilename(file.getOriginalFilename());
            // Using a flat structure since we are in a dedicated resource bucket
            String fileName = userId + "-" + System.currentTimeMillis() + extension;

            uploadToS3(resourceBucket, fileName, contentType, file);

            return resourcePublicUrl + "/" + fileName;
        } catch (Exception e) {
            log.error("Failed to upload resource file to R2: {}", e.getMessage());
            throw new RuntimeException("Failed to upload resource file", e);
        }
    }

    /**
     * Downloads an image from a social provider URL and stores it in the Avatar Bucket
     */
    @Override
    public String uploadFromUrl(String externalUrl, String userId) {
        if (externalUrl == null || !externalUrl.startsWith("http")) {
            throw new InvalidImageException("Invalid URL format");
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(externalUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("Failed to download image from social provider: {}", externalUrl);
                throw new ResourceNotFoundException("Provided URL is not accessible");
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");

            if (!isValidImageContent(contentType)) {
                throw new InvalidImageException("URL does not point to a valid image type");
            }

            String extension = getExtensionFromContentType(contentType);
            String fileName = userId + "-social" + extension;

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(avatarBucket)
                            .key(fileName)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(response.body())
            );

            return avatarPublicUrl + "/" + fileName;

        } catch (Exception e) {
            log.error("Failed to sync social image to R2: {}", e.getMessage());
            // Fallback: return original URL so the profile still has an image
            return externalUrl;
        }
    }

    /**
     * Deletes a file from the appropriate R2 bucket based on its URL
     */
    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        // FIX: Must use AND (&&) here. If we used OR (||), a valid avatar URL would trigger 
        // the return because it doesn't start with the resource URL.
        if (!fileUrl.startsWith(avatarPublicUrl) && !fileUrl.startsWith(resourcePublicUrl)) {
            return;
        }

        String targetBucket = null;
        String targetKey = null;

        if (fileUrl.startsWith(avatarPublicUrl)) {
            targetBucket = avatarBucket;
            targetKey = fileUrl.substring(avatarPublicUrl.length() + 1);
        } else if (fileUrl.startsWith(resourcePublicUrl)) {
            targetBucket = resourceBucket;
            targetKey = fileUrl.substring(resourcePublicUrl.length() + 1);
        }

        if (targetBucket != null && targetKey != null) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(targetBucket)
                        .key(targetKey)
                        .build());
                log.info("Deleted object from bucket {}: {}", targetBucket, targetKey);
            } catch (Exception e) {
                log.error("Failed to delete from R2: {}. Error: {}", fileUrl, e.getMessage());
            }
        }
    }

    // --- Private Helpers ---

    private void uploadToS3(String bucket, String key, String contentType, MultipartFile file) throws Exception {
        PutObjectRequest putObj = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObj, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) return ".jpg";
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String getExtensionFromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".pdf";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }

    private boolean isValidImageContent(String contentType) {
        if (contentType == null) return false;
        String type = contentType.toLowerCase();
        return type.equals("image/jpeg") || type.equals("image/jpg") || 
               type.equals("image/png") || type.equals("image/gif") || type.equals("image/webp");
    }

    private boolean isValidResourceFileType(String contentType, String filename) {
        String ext = getExtensionFromFilename(filename);
        boolean extAllowed = ext.equals(".pdf") || ext.equals(".doc") || ext.equals(".docx") || ext.equals(".txt");
        if (!extAllowed) return false;

        if (contentType == null || contentType.isBlank()) return true;
        
        String type = contentType.toLowerCase();
        return type.equals("application/pdf") ||
                type.equals("application/msword") ||
                type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                type.equals("text/plain") ||
                type.equals("application/octet-stream");
    }
}