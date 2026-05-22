package com.example.solimus.repositories;

import com.example.solimus.entities.Payment;
import com.example.solimus.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByInterventionRequestIdAndType(Long requestId, PaymentType type);
    java.util.List<Payment> findAllByProviderIdOrderByCreatedAtDesc(Long providerId);
    Optional<Payment> findByReference(String reference);

    /**
     * Calcule le total des paiements validés reçus par un prestataire pour une date précise.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.provider.id = :providerId " +
           "AND CAST(p.createdAt AS date) = :date " +
           "AND p.status = com.example.solimus.enums.PaymentStatus.COMPLETED")
    BigDecimal sumByProviderIdAndDate(
        @Param("providerId") Long providerId,
        @Param("date") LocalDate date);

    /**
     * Calcule le total des paiements validés reçus par un prestataire dans un intervalle de dates donné.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.provider.id = :providerId " +
           "AND CAST(p.createdAt AS date) BETWEEN :startDate AND :endDate " +
           "AND p.status = com.example.solimus.enums.PaymentStatus.COMPLETED")
    BigDecimal sumByProviderIdBetween(
        @Param("providerId") Long providerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}
