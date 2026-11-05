package com.dworf.slots_demo.repository;

import com.dworf.slots_demo.entity.SystemBankroll;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemBankrollRepository extends JpaRepository<SystemBankroll, Long> {

    /**
     * SELECT ... FOR UPDATE on the single bankroll row. Must be called inside a transaction;
     * concurrent case openings are serialized here.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM SystemBankroll b WHERE b.id = :id")
    Optional<SystemBankroll> findByIdForUpdate(@Param("id") Long id);
}

