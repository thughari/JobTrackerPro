package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.CreateCareerResourceRequest;
import com.thughari.jobtrackerpro.dto.UpdateCareerResourceRequest;
import com.thughari.jobtrackerpro.entity.CareerResource;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.CareerResourceRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CareerResourceServiceTest {

    @Mock private CareerResourceRepository resourceRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private MultipartFile multipartFile;

    @InjectMocks
    private CareerResourceService service;

    @Test
    void getResourcePageSanitizesInput() {
        when(resourceRepository.findAll(ArgumentMatchers.<Specification<CareerResource>>any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(), pageable, 0);
                });

        var response = service.getResourcePage(-1, 1000, " all ", " all ", "bad", null, null, null);

        assertEquals(0, response.getPage());
        assertEquals(50, response.getSize());
    }

    @Test
    void createResourceRejectsDuplicateUrl() {
        CreateCareerResourceRequest req = new CreateCareerResourceRequest();
        req.setTitle("A");
        req.setCategory("B");
        req.setUrl("https://example.com");

        when(resourceRepository.existsByUrl("https://example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createResource("user@example.com", req));
    }

    @Test
    void createResourceFromFileUploadsAndStoresMetadata() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@example.com");
        user.setName("User");

        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("guide.pdf");
        when(multipartFile.getSize()).thenReturn(123L);
        when(storageService.uploadResourceFile(multipartFile, user.getId().toString())).thenReturn("https://cdn/file.pdf");
        when(resourceRepository.save(any(CareerResource.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCareerResourceRequest req = new CreateCareerResourceRequest();
        req.setTitle(" Title ");
        req.setCategory(" Prep ");
        req.setDescription(" Desc ");
        
        var dto = service.createResourceFromFile("u@example.com", req, multipartFile);

        assertEquals("FILE", dto.getResourceType());
        assertEquals("guide.pdf", dto.getOriginalFileName());
        assertTrue(dto.isOwnedByCurrentUser());
    }

    @Test
    void updateResourceRejectsUnauthorized() {
        UUID id = UUID.randomUUID();
        CareerResource resource = new CareerResource();
        resource.setSubmittedByEmail("owner@example.com");

        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));

        UpdateCareerResourceRequest req = new UpdateCareerResourceRequest();
        req.setTitle("Title");
        req.setCategory("Category");

        assertThrows(IllegalArgumentException.class, () -> service.updateResource("other@example.com", id, req));
    }

    @Test
    void deleteResourceDeletesFileForFileType() {
        UUID id = UUID.randomUUID();
        CareerResource resource = new CareerResource();
        resource.setSubmittedByEmail("owner@example.com");
        resource.setResourceType("FILE");
        resource.setUrl("https://cdn/path/file.pdf");

        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));

        service.deleteResource("owner@example.com", id);

        verify(storageService).deleteFile("https://cdn/path/file.pdf");
        verify(resourceRepository).delete(resource);
    }
}
