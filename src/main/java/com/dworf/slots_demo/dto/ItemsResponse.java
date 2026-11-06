package com.dworf.slots_demo.dto;

import java.util.List;

public record ItemsResponse(
        String username,
        List<String> items
) {
}
