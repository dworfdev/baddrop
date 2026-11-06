package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.CaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record CaseResponse(
        Long id,
        String slug,
        String name,
        BigDecimal price,
        String imageUrl,
        @JsonProperty("image_url") String imageUrlSnake,
        Instant createdAt,
        @JsonProperty("created_at") Instant createdAtSnake
) {
    public static CaseResponse fromEntity(CaseEntity entity) {
        return new CaseResponse(
                entity.getId(),
                entity.getSlug(),
                entity.getName(),
                entity.getPrice(),
                entity.getImageUrl(),
                entity.getImageUrl(),
                entity.getCreatedAt(),
                entity.getCreatedAt()
        );
    }
}

