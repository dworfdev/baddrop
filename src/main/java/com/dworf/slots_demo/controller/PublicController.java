package com.dworf.slots_demo.controller;

import com.dworf.slots_demo.dto.CaseResponse;
import com.dworf.slots_demo.dto.LeaderboardEntryResponse;
import com.dworf.slots_demo.dto.LiveFeedEventResponse;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.PlayService;
import com.dworf.slots_demo.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything here is deliberately public and contains no account data (see AuthInterceptor.PUBLIC). */
@RestController
public class PublicController {
    private final PlayService play;
    private final WalletService wallet;

    public PublicController(PlayService play, WalletService wallet) {
        this.play = play;
        this.wallet = wallet;
    }

    @GetMapping("/api")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("application", "BADDROP");
        m.put("status", "RUNNING");
        return m;
    }

    @GetMapping("/api/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("depositAdminUsername", wallet.getDepositAdminUsername());
        m.put("minDepositAmount", WalletService.MIN_DEPOSIT_AMOUNT);
        m.put("minWithdrawAmount", WalletService.MIN_WITHDRAW_AMOUNT);
        return m;
    }

    @GetMapping("/api/cases")
    public List<CaseResponse> cases() {
        return play.activeCases();
    }

    @GetMapping("/api/cases/math")
    public List<CaseOpenerEngine.CaseMathStats> math() {
        return play.mathStats();
    }

    @GetMapping("/api/live-feed")
    public List<LiveFeedEventResponse> liveFeed() {
        return play.liveFeed();
    }

    @GetMapping("/api/leaderboard")
    public List<LeaderboardEntryResponse> leaderboard() {
        return play.leaderboard();
    }
}
