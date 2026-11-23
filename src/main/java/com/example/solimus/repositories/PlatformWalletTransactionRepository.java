package com.example.solimus.repositories;

import com.example.solimus.entities.PlatformWalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PlatformWalletTransactionRepository extends JpaRepository<PlatformWalletTransaction, Long> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PlatformWalletTransaction t")
    BigDecimal sumAll();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PlatformWalletTransaction t "
            + "WHERE t.transactionDate >= :from AND t.transactionDate < :to")
    BigDecimal sumInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = {"interventionRequest", "provider", "syndic"})
    @Query("SELECT t FROM PlatformWalletTransaction t "
            + "WHERE t.transactionDate >= :from AND t.transactionDate < :to "
            + "ORDER BY t.transactionDate DESC, t.id DESC")
    Page<PlatformWalletTransaction> findInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                 Pageable pageable);
}
