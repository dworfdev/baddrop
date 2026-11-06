package com.dworf.slots_demo.dto;

public record ItemOperationRequest(
        String username,
        String password,
        String item
) {
}
