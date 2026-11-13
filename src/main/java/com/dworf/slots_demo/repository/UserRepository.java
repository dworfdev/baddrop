package com.dworf.slots_demo.repository;

import com.dworf.slots_demo.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Includes soft-deleted rows: a deleted username stays reserved. */
    boolean existsByUsername(String username);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") Long id);

    List<User> findTop3ByDeletedAtIsNullOrderByBalanceDesc();

    /** Includes soft-deleted users: their balance is still part of the books. */
    @Query("SELECT COALESCE(SUM(u.balance), 0) FROM User u")
    java.math.BigDecimal sumAllBalances();
}
