package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.BalanceResponse;
import com.dworf.slots_demo.dto.CaseResponse;
import com.dworf.slots_demo.dto.ItemsResponse;
import com.dworf.slots_demo.dto.LeaderboardEntryResponse;
import com.dworf.slots_demo.dto.LiveFeedEventResponse;
import com.dworf.slots_demo.dto.OpenCaseResponse;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.CaseEntity;
import com.dworf.slots_demo.entity.User;
import com.dworf.slots_demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Marketplace / opening flows. C5: every state-changing call takes the user's row lock first,
 * which serialises concurrent buys/opens for one account (no double-spend, no double-open).
 */
@Service
public class PlayService {
    private static final int MAX_LIVE_FEED_SIZE = 2;

    private final UserRepository userRepository;
    private final CaseOpeningService caseOpeningService;
    private final CaseOpenerEngine caseOpenerEngine;
    private final IdempotencyService idempotency;
    private final Clock clock;
    private final Deque<LiveFeedEventResponse> liveFeed = new ArrayDeque<>(MAX_LIVE_FEED_SIZE);

    public PlayService(UserRepository userRepository, CaseOpeningService caseOpeningService,
                       CaseOpenerEngine caseOpenerEngine, IdempotencyService idempotency, Clock clock) {
        this.userRepository = userRepository;
        this.caseOpeningService = caseOpeningService;
        this.caseOpenerEngine = caseOpenerEngine;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional
    public UserResponse buy(Long userId, String slug, int quantity, String idempotencyKey) {
        User user = lockActive(userId);
        idempotency.claim(userId, idempotencyKey, "BUY");
        return caseOpeningService.buyCase(user, requireSlug(slug), quantity);
    }

    @Transactional
    public OpenCaseResponse open(Long userId, String slug, int quantity) {
        User user = lockActive(userId);
        OpenCaseResponse res = caseOpeningService.openCaseForUser(user, requireSlug(slug), quantity);
        recordFeed(res);
        return res;
    }

    @Transactional
    public OpenCaseResponse instantOpen(Long userId, String slug, int quantity, String idempotencyKey) {
        User user = lockActive(userId);
        idempotency.claim(userId, idempotencyKey, "INSTANT_OPEN");
        OpenCaseResponse res = caseOpeningService.instantOpenCase(user, requireSlug(slug), quantity);
        recordFeed(res);
        return res;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(Long userId) {
        User u = active(userId);
        return new BalanceResponse(u.getUsername(), u.getBalance());
    }

    @Transactional(readOnly = true)
    public ItemsResponse items(Long userId) {
        User u = active(userId);
        return new ItemsResponse(u.getUsername(), List.copyOf(u.getItems()));
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> leaderboard() {
        return userRepository.findTop3ByDeletedAtIsNullOrderByBalanceDesc().stream()
                .map(LeaderboardEntryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> activeCases() {
        return caseOpeningService.getActiveCases();
    }

    @Transactional(readOnly = true)
    public List<CaseOpenerEngine.CaseMathStats> mathStats() {
        return caseOpeningService.getAllCasesMathStats();
    }

    public List<LiveFeedEventResponse> liveFeed() {
        synchronized (liveFeed) {
            return List.copyOf(liveFeed);
        }
    }

    private void recordFeed(OpenCaseResponse res) {
        String caseName = res.openedCase();
        try {
            CaseEntity c = caseOpeningService.findCaseBySlugOrThrow(res.openedCase());
            if (c.getName() != null && !c.getName().isBlank()) caseName = c.getName();
        } catch (RuntimeException ignored) {
            // feed is cosmetic; never fail a bet because of it
        }
        List<OpenCaseResponse.SpinOutcome> spins = res.spins();
        synchronized (liveFeed) {
            if (spins != null && !spins.isEmpty()) {
                for (OpenCaseResponse.SpinOutcome s : spins) {
                    push(new LiveFeedEventResponse(res.username(), s.wonAmount(), caseName, s.outcomeTier(), clock.instant()));
                }
            } else {
                push(new LiveFeedEventResponse(res.username(), res.wonAmount(), caseName, res.outcomeTier(), clock.instant()));
            }
        }
    }

    private void push(LiveFeedEventResponse e) {
        liveFeed.addFirst(e);
        while (liveFeed.size() > MAX_LIVE_FEED_SIZE) liveFeed.removeLast();
    }

    private User lockActive(Long userId) {
        return userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    private User active(Long userId) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    private static String requireSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case slug must be provided");
        }
        return slug;
    }
}
