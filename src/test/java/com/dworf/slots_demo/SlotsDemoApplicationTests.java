package com.dworf.slots_demo;

import com.dworf.slots_demo.dto.AdminUserOverviewResponse;
import com.dworf.slots_demo.dto.BalanceOperationRequest;
import com.dworf.slots_demo.dto.BalanceResponse;
import com.dworf.slots_demo.dto.ChangePasswordRequest;
import com.dworf.slots_demo.dto.ConfirmDepositRequest;
import com.dworf.slots_demo.dto.DepositSessionResponse;
import com.dworf.slots_demo.dto.ItemOperationRequest;
import com.dworf.slots_demo.dto.ItemsResponse;
import com.dworf.slots_demo.dto.OpenCaseResponse;
import com.dworf.slots_demo.dto.TransactionResponse;
import com.dworf.slots_demo.dto.UserCredentialsRequest;
import com.dworf.slots_demo.dto.UserResponse;
import com.dworf.slots_demo.entity.ItemType;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;
import com.dworf.slots_demo.service.CaseOpenerEngine;
import com.dworf.slots_demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class SlotsDemoApplicationTests {

	@Autowired
	private UserService userService;

	@Test
	void contextLoadsAndPendingTransactionsAndAdminFlowsWork() {
		// 1) Create user & Login
		UserResponse created = userService.createUser(new UserCredentialsRequest("tester", "pass123"));
		assertEquals("tester", created.username());
		assertEquals(0, BigDecimal.ZERO.compareTo(created.balance()));
		assertTrue(created.items().isEmpty());

		// 2) Minimum Deposit ($5 USDT) & Withdrawal Rules (requires >= $10 USDT & >= 1 approved deposit):
		assertThrows(ResponseStatusException.class, () ->
				userService.startDepositSession(new BalanceOperationRequest("tester", "pass123", new BigDecimal("2.00")))
		);
		assertThrows(ResponseStatusException.class, () ->
				userService.withdraw(new BalanceOperationRequest("tester", "pass123", new BigDecimal("15.00"), "UQ_ADDR"))
		);

		// 3-Step Deposit Flow with Cryptographic txHash:
		// Step 1 -> Step 2: Start 15-minute deposit session (status = AWAITING_PAYMENT, not yet visible to admin as PENDING)
		DepositSessionResponse session = userService.startDepositSession(
				new BalanceOperationRequest("tester", "pass123", new BigDecimal("100.00"))
		);
		assertTrue(session.active());
		assertNotNull(session.txHash());
		assertEquals(64, session.txHash().length());
		assertEquals("USDT", session.currency());
		assertEquals("Ton", session.chain());
		assertEquals("UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg", session.address());
		assertEquals("chromakid", session.adminUsername());
		assertTrue(session.remainingSeconds() > 0 && session.remainingSeconds() <= 900);
		assertTrue(userService.getAllPendingTransactions().isEmpty());

		// Active session persists across page reloads
		DepositSessionResponse activeCheck = userService.getActiveDepositSession("tester", "pass123");
		assertTrue(activeCheck.active());
		assertEquals(session.txHash(), activeCheck.txHash());

		// Step 2 -> Step 3 ("Sent" clicked): Promotes deposit from AWAITING_PAYMENT to PENDING
		userService.confirmDepositSent(new ConfirmDepositRequest("tester", "pass123", session.txHash()));
		BalanceResponse beforeApprove = userService.getUserBalance("tester", "pass123");
		assertEquals(0, BigDecimal.ZERO.compareTo(beforeApprove.balance()));

		// Disallow starting a new deposit OR withdraw while user already has a PENDING transaction awaiting admin review
		assertThrows(ResponseStatusException.class, () ->
				userService.startDepositSession(new BalanceOperationRequest("tester", "pass123", new BigDecimal("50.00")))
		);
		assertThrows(ResponseStatusException.class, () ->
				userService.withdraw(new BalanceOperationRequest("tester", "pass123", new BigDecimal("10.00"), "UQ_ADDR"))
		);

		List<TransactionResponse> pendingList = userService.getAllPendingTransactions();
		assertEquals(1, pendingList.size());
		TransactionResponse depositTx = pendingList.get(0);
		assertEquals(session.txHash(), depositTx.txHash());
		assertEquals(TransactionType.DEPOSIT, depositTx.type());
		assertEquals(TransactionStatus.PENDING, depositTx.status());

		// Admin approves deposit by txHash -> balance becomes 100.00
		userService.approveTransaction(depositTx.txHash());
		BalanceResponse afterDepositApprove = userService.getUserBalance("tester", "pass123");
		assertEquals(0, new BigDecimal("100.00").compareTo(afterDepositApprove.balance()));

		// 3) Withdraw Request -> requires >= $10 USDT, >= 1 approved deposit, and non-empty TON address; subtracted immediately (100 - 40 = 60), status is PENDING
		assertThrows(ResponseStatusException.class, () ->
				userService.withdraw(new BalanceOperationRequest("tester", "pass123", new BigDecimal("5.00"), "UQ_ADDR"))
		);
		assertThrows(ResponseStatusException.class, () ->
				userService.withdraw(new BalanceOperationRequest("tester", "pass123", new BigDecimal("40.00"), ""))
		);
		userService.withdraw(new BalanceOperationRequest(
				"tester",
				"pass123",
				new BigDecimal("40.00"),
				"UQCys13azAWTFuERWwjc_5sqKZxQEhJhbtGGaiyp2Sf55Hrg"
		));
		BalanceResponse afterWithdrawRequest = userService.getUserBalance("tester", "pass123");
		assertEquals(0, new BigDecimal("60.00").compareTo(afterWithdrawRequest.balance()));

		AdminUserOverviewResponse adminOverview = userService.getAdminUserOverview("tester");
		assertEquals(1, adminOverview.pendingTransactions().size());
		assertEquals(1, adminOverview.transactionHistory().size());
		assertEquals(TransactionType.DEPOSIT, adminOverview.transactionHistory().get(0).type());
		String withdrawTxHash = adminOverview.pendingTransactions().get(0).txHash();
		assertNotNull(withdrawTxHash);
		assertEquals(64, withdrawTxHash.length());

		// Admin rejects withdrawal by txHash -> 40.00 is refunded back to player (balance becomes 100.00 again)
		userService.rejectTransaction(withdrawTxHash);
		BalanceResponse afterWithdrawReject = userService.getUserBalance("tester", "pass123");
		assertEquals(0, new BigDecimal("100.00").compareTo(afterWithdrawReject.balance()));

		// 3b) Player buys cases on Marketplace ($2 pepe-iron, $5 shiba-inu-gold, $11 doge-amethyst)
		userService.buyItem(new ItemOperationRequest("tester", "pass123", ItemType.IRON_CRATE));
		userService.buyItem(new ItemOperationRequest("tester", "pass123", ItemType.SILVER_CRATE));
		userService.buyItem(new ItemOperationRequest("tester", "pass123", ItemType.GOLD_CRATE));
		BalanceResponse afterBuyingCases = userService.getUserBalance("tester", "pass123");
		// 100 - (2 + 5 + 11) = 82.00
		assertEquals(0, new BigDecimal("82.00").compareTo(afterBuyingCases.balance()));

		ItemsResponse itemsResponse = userService.getUserItems("tester", "pass123");
		assertEquals(List.of(ItemType.IRON_CRATE, ItemType.SILVER_CRATE, ItemType.GOLD_CRATE), itemsResponse.items());

		// 4) Configurable Case Math & Higher Dispersion Validation (5x - 10x Cap Prizes, 93.0% RTP)
		List<CaseOpenerEngine.CaseMathStats> mathStats = userService.getAllCasesMathStats();
		assertEquals(3, mathStats.size());
		for (CaseOpenerEngine.CaseMathStats stat : mathStats) {
			assertEquals(1.0, stat.totalProbability(), 0.0001);
			assertTrue(stat.rtpPercent() >= 92.0 && stat.rtpPercent() <= 94.0);
			assertTrue(stat.variance() > 3.0);
		}

		OpenCaseResponse openIron = userService.openCase(
				new ItemOperationRequest("tester", "pass123", ItemType.IRON_CRATE)
		);
		assertEquals(ItemType.IRON_CRATE, openIron.openedCase());
		assertTrue(openIron.wonAmount().compareTo(BigDecimal.ZERO) >= 0);
		assertEquals(
				0,
				new BigDecimal("82.00").add(openIron.wonAmount()).compareTo(openIron.newBalance())
		);
		assertEquals(List.of(ItemType.SILVER_CRATE, ItemType.GOLD_CRATE), openIron.remainingItems());

		// 5) Promo code validation
		Map<String, Object> validPromo = userService.verifyPromoCode("admin1234");
		assertEquals(true, validPromo.get("valid"));
		assertThrows(ResponseStatusException.class, () -> userService.verifyPromoCode("wrongcode"));

		// 6) Change password, logout & delete account
		userService.changePassword(new ChangePasswordRequest("tester", "pass123", "newPass456"));
		Map<String, String> logoutResult = userService.logoutUser(new UserCredentialsRequest("tester", "newPass456"));
		assertEquals("LOGGED_OUT", logoutResult.get("status"));

		Map<String, String> deleteResult = userService.deleteAccount(new UserCredentialsRequest("tester", "newPass456"));
		assertEquals("DELETED", deleteResult.get("status"));
		assertTrue(userService.getAllUsers().isEmpty());
	}

	/**
	 * Simulates 5 simultaneous POST /confirm requests firing at the exact same instant
	 * for the same active deposit session. Proves that pessimistic locking + atomic SQL
	 * status transition allows EXACTLY 1 request to succeed and forces the other 4 to fail.
	 */
	@Test
	void concurrentConfirmDepositRequestsOnlyOneSucceedsAndFourFail() throws Exception {
		userService.createUser(new UserCredentialsRequest("race_tester", "secret123"));
		DepositSessionResponse session = userService.startDepositSession(
				new BalanceOperationRequest("race_tester", "secret123", new BigDecimal("250.00"))
		);
		String txHash = session.txHash();

		int threadCount = 5;
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch readyLatch = new CountDownLatch(threadCount);
		CountDownLatch startGate = new CountDownLatch(1);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (int i = 1; i <= threadCount; i++) {
			final int workerId = i;
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				readyLatch.countDown();
				try {
					startGate.await();
					userService.confirmDepositSent(
							new ConfirmDepositRequest("race_tester", "secret123", txHash)
					);
					int s = successCount.incrementAndGet();
					System.out.printf("[CONCURRENCY-TEST] Worker #%d SUCCEEDED (txHash=%s, totalSuccess=%d)%n",
							workerId, txHash.substring(0, 12), s);
				} catch (Exception ex) {
					int f = failureCount.incrementAndGet();
					System.out.printf("[CONCURRENCY-TEST] Worker #%d FAILED as expected: %s (totalFailed=%d)%n",
							workerId, ex.getMessage(), f);
				}
			}, pool);
			futures.add(future);
		}

		// Wait until all 5 worker threads are poised at the barrier, then release them simultaneously
		assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
		startGate.countDown();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
		pool.shutdownNow();

		System.out.printf("[CONCURRENCY-TEST SUMMARY] Successes=%d | Failures=%d%n",
				successCount.get(), failureCount.get());

		assertEquals(1, successCount.get(), "Exactly 1 concurrent confirm request must succeed");
		assertEquals(4, failureCount.get(), "Exactly 4 concurrent confirm requests must fail");

		List<TransactionResponse> userPending = userService.getUserPendingTransactions("race_tester");
		assertEquals(1, userPending.size(), "Only 1 PENDING transaction may exist in DB");
		assertEquals(txHash, userPending.get(0).txHash());
		assertEquals(TransactionStatus.PENDING, userPending.get(0).status());

		// Cleanup test user
		userService.deleteAccount(new UserCredentialsRequest("race_tester", "secret123"));
	}

}
