package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.ItemType;

import java.util.List;

public record ItemsResponse(
        String username,
        List<ItemType> items
) {
}

