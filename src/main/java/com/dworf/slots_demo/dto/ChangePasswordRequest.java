package com.dworf.slots_demo.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
