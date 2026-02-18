package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.CareerResourceDTO;
import com.thughari.jobtrackerpro.dto.CareerResourcePageResponse;
import com.thughari.jobtrackerpro.dto.CreateCareerResourceRequest;
import com.thughari.jobtrackerpro.dto.UpdateCareerResourceRequest;
import com.thughari.jobtrackerpro.entity.CareerResource;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.CareerResourceRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

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
    @Cacheable(value = "resourcePages", 
               key = "{#page, #size, #query ?: '', #category ?: '', #type ?: '', #viewerEmail ?: 'anon'}")
    public CareerResourcePageResponse getResourcePage(int page, int size, String query, 
                                                      String category, String type, String viewerEmail) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        
        String normalizedQuery = normalizeFilter(query);
        String normalizedCategory = normalizeFilter(category);
        String normalizedType = normalizeType(type);

        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var resourcePage = resourceRepository.findAll(buildResourceFilter(normalizedQuery, normalizedCategory, normalizedType), pageable);

        var content = resourcePage.getContent().stream()
                .map(resource -> toDTO(resource, viewerEmail))
                .toList();

        return new CareerResourcePageResponse(
                content, resourcePage.getNumber(), resourcePage.getSize(),
                resourcePage.getTotalElements(), resourcePage.getTotalPages(), resourcePage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "resourceCategories")
    public List<String> getAllCategories() {
        return resourceRepository.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "userResources", key = "#email")
    public List<CareerResourceDTO> getMyResources(String email) {
        return resourceRepository.findAllBySubmittedByEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(resource -> toDTO(resource, email))
                .toList();
    }

    @Caching(evict = {
        @CacheEvict(value = "resourcePages", allEntries = true),
        @CacheEvict(value = "resourceCategories", allEntries = true),
        @CacheEvict(value = "userResources", key = "#email")
    })
    public CareerResourceDTO createResource(String email, CreateCareerResourceRequest request) {
        String normalizedUrl = normalizeUrl(request.getUrl());
        if (resourceRepository.existsByUrl(normalizedUrl)) {
            throw new IllegalArgumentException("This resource link is already in the vault!");
        }
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

    @Caching(evict = {
        @CacheEvict(value = "resourcePages", allEntries = true),
        @CacheEvict(value = "resourceCategories", allEntries = true),
        @CacheEvict(value = "userResources", key = "#email")
    })
    public CareerResourceDTO createResourceFromFile(String email, String title, String category, 
                                                    String description, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File required");
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

    @Caching(evict = {
        @CacheEvict(value = "resourcePages", allEntries = true),
        @CacheEvict(value = "resourceCategories", allEntries = true),
        @CacheEvict(value = "userResources", key = "#email")
    })
    public void deleteResource(String email, UUID resourceId) {
        CareerResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));

        if (!resource.getSubmittedByEmail().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("Unauthorized");
        }

        if ("FILE".equalsIgnoreCase(resource.getResourceType())) {
            storageService.deleteFile(resource.getUrl());
        }
        resourceRepository.delete(resource);
    }

    @Caching(evict = {
        @CacheEvict(value = "resourcePages", allEntries = true),
        @CacheEvict(value = "resourceCategories", allEntries = true),
        @CacheEvict(value = "userResources", key = "#email")
    })
    public CareerResourceDTO updateResource(String email, UUID resourceId, UpdateCareerResourceRequest request) {
        CareerResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));

        if (!resource.getSubmittedByEmail().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("Unauthorized");
        }

        validateCommonFields(request.getTitle(), request.getCategory());
        resource.setTitle(request.getTitle().trim());
        resource.setCategory(request.getCategory().trim());
        resource.setDescription(request.getDescription() == null ? null : request.getDescription().trim());

        if ("LINK".equalsIgnoreCase(resource.getResourceType())) {
            resource.setUrl(normalizeUrl(request.getUrl()));
        }

        return toDTO(resourceRepository.save(resource), email);
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
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("category")), category.toLowerCase(Locale.ROOT)));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("resourceType")), type));
            }
            return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String normalizeFilter(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return (trimmed.isEmpty() || "all".equalsIgnoreCase(trimmed)) ? null : trimmed;
    }

    private String normalizeType(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null) return null;
        String upper = normalized.toUpperCase(Locale.ROOT);
        return (upper.equals("LINK") || upper.equals("FILE")) ? upper : null;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void applySubmitter(CareerResource resource, User user) {
        resource.setSubmittedByEmail(user.getEmail());
        resource.setSubmittedByName(user.getName() == null || user.getName().isBlank() ? user.getEmail() : user.getName());
    }

    private void validateLinkPayload(CreateCareerResourceRequest request, String normalizedUrl) {
        validateCommonFields(request.getTitle(), request.getCategory());
        if (normalizedUrl == null || normalizedUrl.isBlank()) throw new IllegalArgumentException("Valid URL required");
    }

    private void validateCommonFields(String title, String category) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("Category required");
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        
        String trimmed = url.trim();

        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        	throw new IllegalArgumentException("Invalid URL format. Please provide a real website link.");
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