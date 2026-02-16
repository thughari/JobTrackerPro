package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.CareerResourceDTO;
import com.thughari.jobtrackerpro.dto.CreateCareerResourceRequest;
import com.thughari.jobtrackerpro.service.CareerResourceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources")
public class CareerResourceController {

    private final CareerResourceService careerResourceService;

    public CareerResourceController(CareerResourceService careerResourceService) {
        this.careerResourceService = careerResourceService;
    }

    @GetMapping
    public ResponseEntity<List<CareerResourceDTO>> getResources() {
        return ResponseEntity.ok(careerResourceService.getAllResources());
    }

    @PostMapping
    public ResponseEntity<CareerResourceDTO> addResource(@RequestBody CreateCareerResourceRequest request) {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(careerResourceService.createResource(email, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CareerResourceDTO> addResourceFile(
            @RequestParam String title,
            @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam MultipartFile file
    ) {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(careerResourceService.createResourceFromFile(email, title, category, description, file));
    }

    @DeleteMapping("/{resourceId}")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID resourceId) {
        String email = getAuthenticatedEmail();
        careerResourceService.deleteResource(email, resourceId);
        return ResponseEntity.noContent().build();
    }

    private String getAuthenticatedEmail() {
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }
}
