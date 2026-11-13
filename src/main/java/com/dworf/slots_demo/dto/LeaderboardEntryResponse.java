package com.dworf.slots_demo.dto;

import com.dworf.slots_demo.entity.User;

import java.math.BigDecimal;

/** Public leaderboard: masked name only, never ids or item lists. */
public record LeaderboardEntryResponse(String username, BigDecimal balance) {
    public static LeaderboardEntryResponse from(User u) {
        String n = u.getUsername();
        String masked = n.length() <= 2 ? n.charAt(0) + "*" : n.substring(0, 2) + "***";
        return new LeaderboardEntryResponse(masked, u.getBalance());
    }
}
