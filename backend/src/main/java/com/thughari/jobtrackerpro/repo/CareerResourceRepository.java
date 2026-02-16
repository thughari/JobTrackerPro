package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.CareerResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CareerResourceRepository extends JpaRepository<CareerResource, UUID>, JpaSpecificationExecutor<CareerResource> {
    List<CareerResource> findAllByOrderByCreatedAtDesc();

    Page<CareerResource> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByUrl(String url);
}
