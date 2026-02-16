package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.CareerResourceDTO;
import com.thughari.jobtrackerpro.dto.CareerResourcePageResponse;
import com.thughari.jobtrackerpro.dto.CreateCareerResourceRequest;
import com.thughari.jobtrackerpro.entity.CareerResource;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.CareerResourceRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Locale;

@Service
@Transactional
public class CareerResourceService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CareerResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public CareerResourceService(CareerResourceRepository resourceRepository,
                                 UserRepository userRepository,
                                 StorageService storageService) {
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "resourcePages", key = "{#page, #size, #query == null ? '' : #query, #category == null ? '' : #category, #type == null ? '' : #type, #viewerEmail == null ? 'anon' : #viewerEmail}")
    public CareerResourcePageResponse getResourcePage(int page,
                                                      int size,
                                                      String query,
                                                      String category,
                                                      String type,
                                                      String viewerEmail) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        String normalizedQuery = normalizeFilter(query);
        String normalizedCategory = normalizeFilter(category);
        String normalizedType = normalizeType(type);

        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var resourcePage = resourceRepository.findAll(buildResourceFilter(normalizedQuery, normalizedCategory, normalizedType), pageable);

        var content = resourcePage.getContent()
                .stream()
                .map(resource -> toDTO(resource, viewerEmail))
                .toList();

        return new CareerResourcePageResponse(
                content,
                resourcePage.getNumber(),
                resourcePage.getSize(),
                resourcePage.getTotalElements(),
                resourcePage.getTotalPages(),
                resourcePage.hasNext()
        );
    }

    private Specification<CareerResource> buildResourceFilter(String query, String category, String type) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (query != null) {
                String likeQuery = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("category")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("submittedByName")), likeQuery)
                ));
            }

            if (category != null) {
                String likeCategory = "%" + category.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("category")), likeCategory));
            }

            if (type != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("resourceType")), type));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty() || "all".equalsIgnoreCase(trimmed)) {
            return null;
        }

        return trimmed;
    }

    private String normalizeType(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            return null;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!upper.equals("LINK") && !upper.equals("FILE")) {
            return null;
        }

        return upper;
    }

    @CacheEvict(value = "resourcePages", allEntries = true)
    public CareerResourceDTO createResource(String email, CreateCareerResourceRequest request) {
        String normalizedUrl = normalizeUrl(request.getUrl());
        validateLinkPayload(request, normalizedUrl);

        User user = getUser(email);

        CareerResource resource = new CareerResource();
        resource.setTitle(request.getTitle().trim());
        resource.setUrl(normalizedUrl);
        resource.setCategory(request.getCategory().trim());
        resource.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        resource.setResourceType("LINK");
        applySubmitter(resource, user);

        return toDTO(resourceRepository.save(resource), email);
    }

    @CacheEvict(value = "resourcePages", allEntries = true)
    public CareerResourceDTO createResourceFromFile(String email,
                                                    String title,
                                                    String category,
                                                    String description,
                                                    MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a file to upload");
        }
        validateCommonFields(title, category);

        User user = getUser(email);
        String fileUrl = storageService.uploadResourceFile(file, user.getId().toString());

        CareerResource resource = new CareerResource();
        resource.setTitle(title.trim());
        resource.setCategory(category.trim());
        resource.setDescription(description == null ? null : description.trim());
        resource.setUrl(fileUrl);
        resource.setResourceType("FILE");
        resource.setOriginalFileName(file.getOriginalFilename());
        resource.setFileSizeBytes(file.getSize());
        applySubmitter(resource, user);

        return toDTO(resourceRepository.save(resource), email);
    }

    @CacheEvict(value = "resourcePages", allEntries = true)
    public void deleteResource(String email, java.util.UUID resourceId) {
        CareerResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));

        if (!resource.getSubmittedByEmail().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("You can only delete resources you added");
        }

        if ("FILE".equalsIgnoreCase(resource.getResourceType())) {
            storageService.deleteFile(resource.getUrl());
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
        resource.setResourceType("LINK");
        resource.setSubmittedByEmail("system@jobtrackerpro.local");
        resource.setSubmittedByName("JobTrackerPro");
        resourceRepository.save(resource);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void applySubmitter(CareerResource resource, User user) {
        resource.setSubmittedByEmail(user.getEmail());
        resource.setSubmittedByName(user.getName() == null || user.getName().isBlank() ? user.getEmail() : user.getName());
    }

    private void validateLinkPayload(CreateCareerResourceRequest request, String normalizedUrl) {
        validateCommonFields(request.getTitle(), request.getCategory());
        if (normalizedUrl == null || normalizedUrl.isBlank()) {
            throw new IllegalArgumentException("Valid URL is required");
        }
    }

    private void validateCommonFields(String title, String category) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private CareerResourceDTO toDTO(CareerResource resource, String viewerEmail) {
        CareerResourceDTO dto = new CareerResourceDTO();
        dto.setId(resource.getId());
        dto.setTitle(resource.getTitle());
        dto.setUrl(resource.getUrl());
        dto.setCategory(resource.getCategory());
        dto.setDescription(resource.getDescription());
        dto.setResourceType(resource.getResourceType());
        dto.setOriginalFileName(resource.getOriginalFileName());
        dto.setFileSizeBytes(resource.getFileSizeBytes());
        dto.setOwnedByCurrentUser(viewerEmail != null && resource.getSubmittedByEmail().equalsIgnoreCase(viewerEmail));
        dto.setSubmittedByName(resource.getSubmittedByName());
        dto.setCreatedAt(resource.getCreatedAt());
        return dto;
    }
}
