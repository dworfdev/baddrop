package com.dworf.slots_demo.dto;

/** The csrfToken must be echoed back in the X-CSRF-Token header on every state-changing request. */
public record MeResponse(UserResponse user, String csrfToken) {
}
