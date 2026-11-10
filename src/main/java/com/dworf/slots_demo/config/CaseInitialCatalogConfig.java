package com.dworf.slots_demo.config;

import com.dworf.slots_demo.entity.CaseEntity;
import com.dworf.slots_demo.repository.CaseRepository;
import com.dworf.slots_demo.service.BankrollSplit;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Loads initial case definitions and paytables from {@code classpath:initial-cases.json}
 * via {@link ObjectMapper} and seeds the {@code cases} table on application startup.
 */
@Configuration
public class CaseInitialCatalogConfig {

    public record InitialCaseSeed(
            String slug,
            String name,
            BigDecimal price,
            String imageUrl,
            boolean active,
            List<CaseOpenerEngine.PaytableTier> paytable
    ) {
    }

    private static final String INITIAL_CASES_RESOURCE = "initial-cases.json";
    private static final TypeReference<List<InitialCaseSeed>> SEED_LIST_TYPE = new TypeReference<>() {};

    private final CaseRepository caseRepository;
    private final CaseOpenerEngine caseOpenerEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CaseInitialCatalogConfig(CaseRepository caseRepository, CaseOpenerEngine caseOpenerEngine) {
        this.caseRepository = caseRepository;
        this.caseOpenerEngine = caseOpenerEngine;
    }

    @PostConstruct
    public void seedInitialCases() {
        ClassPathResource resource = new ClassPathResource(INITIAL_CASES_RESOURCE);
        if (!resource.exists()) {
            return;
        }

        List<InitialCaseSeed> seeds;
        try (InputStream is = resource.getInputStream()) {
            seeds = objectMapper.readValue(is, SEED_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + INITIAL_CASES_RESOURCE + ": " + e.getMessage(), e);
        }

        if (seeds == null || seeds.isEmpty()) {
            return;
        }

        Instant baseTime = Instant.now();
        int total = seeds.size();
        for (int i = 0; i < total; i++) {
            InitialCaseSeed seed = seeds.get(i);
            String paytableJson = objectMapper.writeValueAsString(seed.paytable());
            boolean hasFloorMultiplier = seed.paytable() != null && seed.paytable().stream()
                    .anyMatch(t -> t.probability() > 0.0
                            && t.multiplier() != null
                            && t.multiplier().compareTo(BankrollSplit.FLOOR_RATE) <= 0);
            if (!hasFloorMultiplier) {
                throw new IllegalStateException("CRITICAL ERROR: Case " + seed.slug()
                        + " lacks a floor item (multiplier <= " + BankrollSplit.FLOOR_RATE.toPlainString() + ")");
            }
            List<CaseOpenerEngine.Outcome> outcomes = caseOpenerEngine.toOutcomes(seed.price(), seed.paytable());
            if (!caseOpenerEngine.validateMathematics(seed.slug(), seed.price(), outcomes)) {
                throw new IllegalStateException("Mathematical validation failed for initial case: " + seed.slug());
            }

            var existingOpt = caseRepository.findBySlug(seed.slug());
            if (existingOpt.isPresent()) {
                CaseEntity existing = existingOpt.get();
                if (!paytableJson.equals(existing.getPaytableJson())) {
                    existing.setPaytableJson(paytableJson);
                    caseRepository.saveAndFlush(existing);
                }
                continue;
            }

            Instant createdAt = baseTime.minusSeconds(total - 1 - i);
            try {
                caseRepository.saveAndFlush(new CaseEntity(
                        seed.slug(),
                        seed.name(),
                        seed.price(),
                        seed.imageUrl(),
                        seed.active(),
                        paytableJson,
                        createdAt
                ));
            } catch (DataIntegrityViolationException ignored) {
                // concurrently seeded
            }
        }
    }
}

