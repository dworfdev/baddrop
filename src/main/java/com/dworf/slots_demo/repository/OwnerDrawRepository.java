package com.dworf.slots_demo.repository;

import com.dworf.slots_demo.entity.OwnerDraw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OwnerDrawRepository extends JpaRepository<OwnerDraw, Long> {

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM OwnerDraw d")
    BigDecimal sumAmount();

    List<OwnerDraw> findTop50ByOrderByIdDesc();
}
