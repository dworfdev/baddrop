package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.ItemType;

public record ItemOperationRequest(
        String username,
        String password,
        ItemType item
) {
}

