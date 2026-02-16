package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.CareerResourceDTO;
import com.thughari.jobtrackerpro.entity.CareerResource;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.CareerResourceRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CareerResourceService {

    private final CareerResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public CareerResourceService(
            CareerResourceRepository resourceRepository,
            UserRepository userRepository,
            StorageService storageService
    ) {
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<CareerResourceDTO> getAllResources() {
        return resourceRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public CareerResourceDTO createResource(
            String email,
            String title,
            String url,
            String category,
            String description,
            MultipartFile file
    ) {
        String normalizedUrl = normalizeUrl(url);
        validateBasicFields(title, category);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String uploadedFileUrl = null;
        if (file != null && !file.isEmpty()) {
            uploadedFileUrl = storageService.uploadResourceFile(file, user.getId().toString());
        }

        ensureLinkOrFileExists(normalizedUrl, uploadedFileUrl);

        CareerResource resource = new CareerResource();
        resource.setTitle(title.trim());
        resource.setUrl(normalizedUrl);
        resource.setFileUrl(uploadedFileUrl);
        resource.setCategory(category.trim());
        resource.setDescription(normalizeOptional(description));
        resource.setSubmittedByEmail(user.getEmail());
        resource.setSubmittedByName(user.getName() == null || user.getName().isBlank() ? user.getEmail() : user.getName());

        return toDTO(resourceRepository.save(resource));
    }

    public CareerResourceDTO updateResource(
            UUID resourceId,
            String requesterEmail,
            String title,
            String url,
            String category,
            String description,
            boolean removeFile,
            MultipartFile file
    ) {
        CareerResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));

        enforceOwner(resource, requesterEmail);

        validateBasicFields(title, category);
        String normalizedUrl = normalizeUrl(url);

        String oldFileUrl = resource.getFileUrl();
        String nextFileUrl = oldFileUrl;

        if (removeFile) {
            nextFileUrl = null;
        }

        if (file != null && !file.isEmpty()) {
            User user = userRepository.findByEmail(requesterEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            nextFileUrl = storageService.uploadResourceFile(file, user.getId().toString());
        }

        ensureLinkOrFileExists(normalizedUrl, nextFileUrl);

        resource.setTitle(title.trim());
        resource.setUrl(normalizedUrl);
        resource.setCategory(category.trim());
        resource.setDescription(normalizeOptional(description));
        resource.setFileUrl(nextFileUrl);

        CareerResource saved = resourceRepository.save(resource);

        if (oldFileUrl != null && !oldFileUrl.isBlank() && !oldFileUrl.equals(nextFileUrl)) {
            storageService.deleteFile(oldFileUrl);
        }

        return toDTO(saved);
    }

    public void deleteResource(UUID resourceId, String requesterEmail) {
        CareerResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));

        enforceOwner(resource, requesterEmail);

        if (resource.getFileUrl() != null && !resource.getFileUrl().isBlank()) {
            storageService.deleteFile(resource.getFileUrl());
        }

        resourceRepository.delete(resource);
    }

    @PostConstruct
    public void seedStarterResources() {
        if (resourceRepository.count() > 0) {
            return;
        }

        addSeedResource("Career Preparation Notes", "https://docs.google.com/document/d/1-25JrPUai6P7pjKk1g7mELpI0YUINS_NpjUk7lsMfyg/edit?tab=t.0#heading=h.kf8l3f8jftc2", "Guides & Study Docs");
        addSeedResource("Main Career Resources Folder", "https://drive.google.com/drive/folders/1ISp9GBv7ih1blEQOPYplD_idG1j0ibGq?usp=sharing", "Drive Folders & File Packs");
        addSeedResource("DSA Folder", "https://drive.google.com/drive/folders/1ei52Zc_cQe0rJK404M56BmEisUjnF6kN?usp=drive_link", "Drive Folders & File Packs");
        addSeedResource("21 Days React Study Plan", "https://thecodedose.notion.site/21-Days-React-Study-Plan-1988ff023cae48459bae8cb20cb75a67", "Structured Learning");
        addSeedResource("Opportunity Tracker Sheet", "https://docs.google.com/spreadsheets/d/1KBFiqJTaFY1164XtglKvn2vAofScCfGlkY-n54D2d14/edit?gid=584790886#gid=584790886", "Trackers & Opportunity Sheets");
    }

    private void addSeedResource(String title, String url, String category) {
        if (resourceRepository.existsByUrl(url)) {
            return;
        }

        CareerResource resource = new CareerResource();
        resource.setTitle(title);
        resource.setUrl(url);
        resource.setCategory(category);
        resource.setDescription("Seeded starter resource");
        resource.setSubmittedByEmail("system@jobtrackerpro.local");
        resource.setSubmittedByName("JobTrackerPro");
        resourceRepository.save(resource);
    }

    private void enforceOwner(CareerResource resource, String requesterEmail) {
        if (!resource.getSubmittedByEmail().equalsIgnoreCase(requesterEmail)) {
            throw new IllegalArgumentException("You can modify only your own resources");
        }
    }

    private void validateBasicFields(String title, String category) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
    }

    private void ensureLinkOrFileExists(String normalizedUrl, String fileUrl) {
        boolean hasUrl = normalizedUrl != null && !normalizedUrl.isBlank();
        boolean hasFile = fileUrl != null && !fileUrl.isBlank();

        if (!hasUrl && !hasFile) {
            throw new IllegalArgumentException("Provide either a URL or a file");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUrl(String url) {
        if (url == null || url.trim().isBlank()) {
            return null;
        }

        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private CareerResourceDTO toDTO(CareerResource resource) {
        CareerResourceDTO dto = new CareerResourceDTO();
        dto.setId(resource.getId());
        dto.setTitle(resource.getTitle());
        dto.setUrl(resource.getUrl());
        dto.setFileUrl(resource.getFileUrl());
        dto.setCategory(resource.getCategory());
        dto.setDescription(resource.getDescription());
        dto.setSubmittedByName(resource.getSubmittedByName());
        dto.setSubmittedByEmail(resource.getSubmittedByEmail());
        dto.setCreatedAt(resource.getCreatedAt());
        return dto;
    }
}
