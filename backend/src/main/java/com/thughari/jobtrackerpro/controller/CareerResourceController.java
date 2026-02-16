package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.CareerResourceDTO;
import com.thughari.jobtrackerpro.dto.CreateCareerResourceRequest;
import com.thughari.jobtrackerpro.service.CareerResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    private String getAuthenticatedEmail() {
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }
}
