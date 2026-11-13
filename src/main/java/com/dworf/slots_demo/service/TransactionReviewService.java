package com.dworf.slots_demo.service;

import com.dworf.slots_demo.dto.AdminUserOverviewResponse;
import com.dworf.slots_demo.dto.TransactionResponse;
import com.dworf.slots_demo.entity.BalanceTransaction;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;
import com.dworf.slots_demo.entity.User;
import com.dworf.slots_demo.repository.BalanceTransactionRepository;
import com.dworf.slots_demo.repository.UserRepository;
import com.dworf.slots_demo.security.TonTxHash;
import com.dworf.slots_demo.security.UsernamePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;

/** Admin approve/reject of PENDING transactions (C5/C6/C8). Lock order: user -> transaction. */
@Service
public class TransactionReviewService {
    private static final Pattern EXTERNAL_HASH = Pattern.compile("^[A-Za-z0-9+/=_-]{16,128}$");

    private final BalanceTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditService audit;
    private final Clock clock;

    public TransactionReviewService(BalanceTransactionRepository transactionRepository, UserRepository userRepository,
                                    AuditService audit, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Deposit: the on-chain hash the player submitted is what gets approved; the admin may type or paste another one
     * (a hash or explorer link) to correct it. Either way it is normalised and can back only ONE deposit (C8).
     * Withdrawal: an optional payout hash for the record.
     */
    @Transactional
    public TransactionResponse approve(String txHash, String externalTxHash, String adminName, String ip) {
        BalanceTransaction tx = lockPending(txHash);
        String external = externalTxHash == null ? null : externalTxHash.trim();
        if (external != null && external.isEmpty()) {
            external = null;
        }
        if (tx.getType() == TransactionType.DEPOSIT) {
            String chosen;
            if (external == null) {
                chosen = tx.getExternalTxHash();
            } else {
                try {
                    chosen = TonTxHash.normalize(external);
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
                }
            }
            if (chosen == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "externalTxHash (the verified on-chain transaction hash) is required to approve a deposit");
            }
            if (!chosen.equals(tx.getExternalTxHash()) && transactionRepository.existsByExternalTxHash(chosen)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This on-chain transaction was already used");
            }
            tx.setExternalTxHash(chosen);
            external = chosen;
        } else if (external != null) {
            if (!EXTERNAL_HASH.matcher(external).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalTxHash has an invalid format");
            }
            if (transactionRepository.existsByExternalTxHash(external)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This on-chain transaction was already used");
            }
            tx.setExternalTxHash(external);
        }
        if (tx.getType() == TransactionType.DEPOSIT) {
            User user = tx.getUser();
            user.setBalance(user.getBalance().add(tx.getAmount()));
            userRepository.save(user);
        }
        tx.setStatus(TransactionStatus.APPROVED);
        tx.markDecided(adminName, clock.instant());
        TransactionResponse res = TransactionResponse.fromEntity(transactionRepository.save(tx));
        audit.record(adminName, "TX_APPROVED", tx.getTxHash(),
                tx.getType() + " " + tx.getAmount() + " user=" + tx.getUser().getUsername() + " ext=" + external, ip);
        return res;
    }

    @Transactional
    public TransactionResponse reject(String txHash, String adminName, String ip) {
        BalanceTransaction tx = lockPending(txHash);
        if (tx.getType() == TransactionType.WITHDRAW) {
            User user = tx.getUser();
            user.setBalance(user.getBalance().add(tx.getAmount()));
            userRepository.save(user);
        }
        String releasedReceipt = tx.getType() == TransactionType.DEPOSIT ? tx.getExternalTxHash() : null;
        if (releasedReceipt != null) {
            // a rejected deposit must not keep the chain hash locked: otherwise someone could submit another
            // player's (public) payment hash first and block its rightful owner. The hash stays in the audit trail.
            tx.setExternalTxHash(null);
        }
        tx.setStatus(TransactionStatus.REJECTED);
        tx.markDecided(adminName, clock.instant());
        TransactionResponse res = TransactionResponse.fromEntity(transactionRepository.save(tx));
        audit.record(adminName, "TX_REJECTED", tx.getTxHash(),
                tx.getType() + " " + tx.getAmount() + " user=" + tx.getUser().getUsername()
                        + (releasedReceipt != null ? " receipt=" + releasedReceipt : ""), ip);
        return res;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> pending() {
        return transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING)
                .stream().map(TransactionResponse::fromEntity).toList();
    }

    /** Looks a player up by username, or by Telegram id when the query is all digits. */
    @Transactional(readOnly = true)
    public AdminUserOverviewResponse overview(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be blank");
        }
        User user = null;
        if (q.matches("\\d{1,18}")) {
            user = userRepository.findByTelegramIdAndDeletedAtIsNull(Long.parseLong(q)).orElse(null);
        }
        if (user == null) {
            String username;
            try {
                username = UsernamePolicy.normalize(q);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be blank");
            }
            user = userRepository.findByUsernameAndDeletedAtIsNull(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }
        List<TransactionResponse> pending = transactionRepository
                .findByUserAndStatusOrderByCreatedAtDesc(user, TransactionStatus.PENDING)
                .stream().map(TransactionResponse::fromEntity).toList();
        List<TransactionResponse> history = transactionRepository
                .findByUserAndStatusOrderByCreatedAtAsc(user, TransactionStatus.APPROVED)
                .stream().map(TransactionResponse::fromEntity).toList();
        return AdminUserOverviewResponse.from(user, pending, history);
    }

    /** Lock order: read tx unlocked to learn the owner -> lock user -> lock tx -> re-check status. */
    private BalanceTransaction lockPending(String txHash) {
        if (txHash == null || txHash.isBlank() || txHash.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction hash must be provided");
        }
        String hash = txHash.trim();
        Long ownerId = transactionRepository.findOwnerIdByTxHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        userRepository.findActiveByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Owner account is not active"));
        BalanceTransaction tx = transactionRepository.findByTxHashForUpdate(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is already " + tx.getStatus());
        }
        return tx;
    }
}
