package com.thughari.jobtrackerpro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.thughari.jobtrackerpro.exception.InvalidImageException;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;

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

@Service
@Slf4j
public class StorageService {

    private final S3Client s3Client;
    
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    public StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadFile(MultipartFile file, String userId) {
    	String contentType = file.getContentType();
        if (!isValidImageContent(contentType)) {
            throw new InvalidImageException("Invalid file type. Only JPG, PNG, GIF, WEBP are allowed.");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
             throw new MaxUploadSizeExceededException(0);
        }
        try {
            String extension = getExtensionFromContentType(file.getContentType());
            String fileName = userId + "-" + System.currentTimeMillis() + extension;
            
            PutObjectRequest putObj = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObj, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return publicUrl + "/" + fileName;
        } catch (Exception e) {
        	log.error("Failed to upload to R2: " + e.getLocalizedMessage());
            throw new RuntimeException("Failed to upload to R2", e);
        }
    }

    // 2. Import from Social URL (Google/GitHub)
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

            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
            	log.error("Failed to download image.");
            	throw new ResourceNotFoundException("provided url is not accessible");
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            
            if (!isValidImageContent(contentType)) {
                throw new InvalidImageException("URL does not point to a valid image (Type: " + contentType + ")");
            }
            
            response.headers().firstValue("Content-Length").ifPresent(len -> {
                if (Long.parseLong(len) > MAX_FILE_SIZE) {
                    throw new IllegalArgumentException("Image at URL is too large");
                }
            });

            String extension = getExtensionFromContentType(contentType);
            String fileName = userId + "-social" + extension;

            PutObjectRequest putObj = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(
                    putObj,
                    RequestBody.fromBytes(response.body())
            );

            return publicUrl + "/" + fileName;

        } catch (InvalidImageException | IllegalArgumentException | ResourceNotFoundException e) {
            throw e;
        }
        catch (Exception e) {
            log.error("Failed to upload image From Url to R2", e);
            throw new InvalidImageException("unable to update image");
        }
    }
    
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }
        if (!fileUrl.startsWith(publicUrl)) {
            return; 
        }

        try {
            String key = fileUrl.substring(publicUrl.length() + 1);

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            
            log.info("Deleted old image from R2: {}", key);

        } catch (Exception e) {
            log.error("Failed to delete file from R2: {}", fileUrl, e);
        }
    }

    
    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) return ".jpg";
        
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
    
    private boolean isValidImageContent(String contentType) {
        if (contentType == null) return false;
        return contentType.equals("image/jpeg") ||
               contentType.equals("image/png") ||
               contentType.equals("image/gif") ||
               contentType.equals("image/webp");
    }
}