package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.CareerResourceDTO;
import com.thughari.jobtrackerpro.service.CareerResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    public ResponseEntity<CareerResourceDTO> addResource(
            @RequestParam String title,
            @RequestParam(required = false) String url,
            @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile file
    ) {
        String email = getAuthenticatedEmail();

        return ResponseEntity.ok(careerResourceService.createResource(
                email,
                title,
                url,
                category,
                description,
                file
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CareerResourceDTO> updateResource(
            @PathVariable UUID id,
            @RequestParam String title,
            @RequestParam(required = false) String url,
            @RequestParam String category,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean removeFile,
            @RequestParam(required = false) MultipartFile file
    ) {
        String email = getAuthenticatedEmail();

        return ResponseEntity.ok(careerResourceService.updateResource(
                id,
                email,
                title,
                url,
                category,
                description,
                removeFile,
                file
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        String email = getAuthenticatedEmail();
        careerResourceService.deleteResource(id, email);
        return ResponseEntity.noContent().build();
    }

    private String getAuthenticatedEmail() {
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }
}
