package com.dworf.slots_demo.dto;

public record ItemOperationRequest(
        String username,
        String password,
        String item,
        Integer quantity
) {
    public ItemOperationRequest(String username, String password, String item) {
        this(username, password, item, 1);
    }

    public int resolvedQuantity() {
        return quantity == null ? 1 : quantity;
    }
}
