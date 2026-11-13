package com.dworf.slots_demo.dto;

public record CaseOperationRequest(String item, Integer quantity) {
    public int resolvedQuantity() {
        return quantity == null ? 1 : quantity;
    }
}
