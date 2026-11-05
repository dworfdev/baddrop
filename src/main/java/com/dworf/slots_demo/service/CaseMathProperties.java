package com.dworf.slots_demo.service;

import com.dworf.slots_demo.entity.ItemType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "slots.cases")
public class CaseMathProperties {

    private Map<ItemType, List<TierConfig>> paytables = new EnumMap<>(ItemType.class);

    public Map<ItemType, List<TierConfig>> getPaytables() {
        return paytables;
    }

    public void setPaytables(Map<ItemType, List<TierConfig>> paytables) {
        this.paytables = paytables;
    }

    public static class TierConfig {
        private BigDecimal multiplier;
        private double probability;
        private String label;

        public TierConfig() {
        }

        public TierConfig(BigDecimal multiplier, double probability, String label) {
            this.multiplier = multiplier;
            this.probability = probability;
            this.label = label;
        }

        public BigDecimal getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(BigDecimal multiplier) {
            this.multiplier = multiplier;
        }

        public double getProbability() {
            return probability;
        }

        public void setProbability(double probability) {
            this.probability = probability;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}