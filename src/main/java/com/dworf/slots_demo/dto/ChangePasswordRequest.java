package com.dworf.slots_demo.dto;

public record ChangePasswordRequest(
        String username,
        String password,
        String newPassword
) {
}

