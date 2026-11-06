package com.dworf.slots_demo.repository;

import com.dworf.slots_demo.entity.CaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseEntity, Long> {

    Optional<CaseEntity> findBySlug(String slug);

    Optional<CaseEntity> findBySlugIgnoreCase(String slug);

    boolean existsBySlug(String slug);

    List<CaseEntity> findByIsActiveTrueOrderByCreatedAtDesc();

    List<CaseEntity> findAllByOrderByCreatedAtDesc();
}

