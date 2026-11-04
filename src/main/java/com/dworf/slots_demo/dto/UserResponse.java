package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.ItemType;
import com.dworf.slots_demo.entity.User;

import java.math.BigDecimal;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        BigDecimal balance,
        List<ItemType> items
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getBalance(),
                List.copyOf(user.getItems())
        );
    }
}

