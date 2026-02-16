package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.CareerResourceDTO;
import com.thughari.jobtrackerpro.dto.CareerResourcePageResponse;
import com.thughari.jobtrackerpro.dto.CreateCareerResourceRequest;
import com.thughari.jobtrackerpro.dto.UpdateCareerResourceRequest;
import com.thughari.jobtrackerpro.service.CareerResourceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/resources")
public class CareerResourceController {

    private final CareerResourceService careerResourceService;

    public CareerResourceController(CareerResourceService careerResourceService) {
        this.careerResourceService = careerResourceService;
    }

    @GetMapping
    public ResponseEntity<CareerResourcePageResponse> getResources(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type
    ) {
        return ResponseEntity.ok(careerResourceService.getResourcePage(page, size, query, category, type, getAuthenticatedEmailOrNull()));
    }


    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(careerResourceService.getAllCategories());
    }

    @PostMapping
    public ResponseEntity<CareerResourceDTO> addResource(@RequestBody CreateCareerResourceRequest request) {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(careerResourceService.createResource(email, request));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<CareerResourceDTO>> getMyResources() {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(careerResourceService.getMyResources(email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CareerResourceDTO> updateResource(@PathVariable UUID id,
                                                            @RequestBody UpdateCareerResourceRequest request) {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(careerResourceService.updateResource(email, id, request));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CareerResourceDTO> uploadResource(
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam MultipartFile file
    ) {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(careerResourceService.createResourceFromFile(email, title, category, description, file));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        String email = getAuthenticatedEmail();
        careerResourceService.deleteResource(email, id);
        return ResponseEntity.noContent().build();
    }

    private String getAuthenticatedEmail() {
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }

    private String getAuthenticatedEmailOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof String email && !"anonymousUser".equalsIgnoreCase(email)) {
            return email.toLowerCase();
        }

        return null;
    }
}
