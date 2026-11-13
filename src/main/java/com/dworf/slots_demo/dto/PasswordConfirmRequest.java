package com.dworf.slots_demo.dto;

public record PasswordConfirmRequest(String password) {
    @Override
    public String toString() {
        return "PasswordConfirmRequest[***]";
    }
}
