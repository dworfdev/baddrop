package com.dworf.slots_demo.dto;

public record AdminLoginRequest(String username, String password, String totp) {
    @Override
    public String toString() {
        return "AdminLoginRequest[username=" + username + "]";
    }
}
