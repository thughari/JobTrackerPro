package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.CareerResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CareerResourceRepository extends JpaRepository<CareerResource, UUID>, JpaSpecificationExecutor<CareerResource> {
    List<CareerResource> findAllByOrderByCreatedAtDesc();

    Page<CareerResource> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<CareerResource> findAllBySubmittedByEmailOrderByCreatedAtDesc(String email);

    @Query("select distinct r.category from CareerResource r where r.category is not null and trim(r.category) <> '' order by r.category asc")
    List<String> findDistinctCategories();

    @Query("select distinct r.category from CareerResource r where upper(r.listingType) = upper(:listingType) and r.category is not null and trim(r.category) <> '' order by r.category asc")
    List<String> findDistinctCategoriesByListingType(String listingType);

    boolean existsByUrl(String url);
}
