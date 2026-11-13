package com.dworf.slots_demo.dto;

public record RegisterRequest(String username, String password) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + "]";
    }
}
