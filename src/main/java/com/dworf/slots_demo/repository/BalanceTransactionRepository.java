package com.dworf.slots_demo.repository;

import com.dworf.slots_demo.entity.BalanceTransaction;
import com.dworf.slots_demo.entity.TransactionStatus;
import com.dworf.slots_demo.entity.TransactionType;
import com.dworf.slots_demo.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {
    List<BalanceTransaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status);
    List<BalanceTransaction> findByUserAndStatusOrderByCreatedAtDesc(User user, TransactionStatus status);
    List<BalanceTransaction> findByUserAndStatusOrderByCreatedAtAsc(User user, TransactionStatus status);
    List<BalanceTransaction> findByUserOrderByCreatedAtDesc(User user);

    boolean existsByUserAndStatus(User user, TransactionStatus status);
    boolean existsByUserAndTypeAndStatus(User user, TransactionType type, TransactionStatus status);

    Optional<BalanceTransaction> findByTxHash(String txHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM BalanceTransaction t WHERE t.txHash = :txHash")
    Optional<BalanceTransaction> findByTxHashForUpdate(@Param("txHash") String txHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BalanceTransaction t SET t.status = :newStatus WHERE t.txHash = :txHash AND t.status = :expectedStatus AND (t.expiresAt IS NULL OR t.expiresAt > :now)")
    int transitionStatusIfCurrent(
            @Param("txHash") String txHash,
            @Param("expectedStatus") TransactionStatus expectedStatus,
            @Param("newStatus") TransactionStatus newStatus,
            @Param("now") Instant now
    );

    void deleteByUser(User user);
}

