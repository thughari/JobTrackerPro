package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.CareerResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CareerResourceRepository extends JpaRepository<CareerResource, UUID> {
    List<CareerResource> findAllByOrderByCreatedAtDesc();

    boolean existsByUrl(String url);
}
