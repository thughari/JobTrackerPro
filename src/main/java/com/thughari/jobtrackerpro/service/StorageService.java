package com.thughari.jobtrackerpro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.thughari.jobtrackerpro.exception.InvalidImageException;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@Slf4j
public class StorageService {

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    public StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadFile(MultipartFile file, String userId) {
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

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("image/jpeg");

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

        } catch (InvalidImageException e) {
            throw e; 
        }
        catch (Exception e) {
            log.error("Failed to sync social image to R2", e);
            throw new InvalidImageException("Only image url provided");
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
}