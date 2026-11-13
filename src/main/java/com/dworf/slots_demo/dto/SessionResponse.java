package com.dworf.slots_demo.dto;

public record SessionResponse(String username, String role, String csrfToken) {
}
