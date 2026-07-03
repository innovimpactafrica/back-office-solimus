package com.example.solimus.repositories;

import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentProvider, Long> {

    /**
     *  Vérifie si un paiement existe pour une intervention et un type donné
     */
    boolean existsByInterventionRequestIdAndType(Long requestId, PaymentType type);

    /**
     * Récupère un paiement par sa référence unique
     */
    Optional<PaymentProvider> findByReference(String reference);

    /**
     * Récupère tous les paiements d'un prestataire triés par date décroissante
     */
    List<PaymentProvider> findAllByProviderIdOrderByCreatedAtDesc(Long providerId);

    /**
     * Calcule le total des paiements validés reçus par un prestataire pour une date précise.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentProvider p " +
           "WHERE p.provider.id = :providerId " +
           "AND CAST(p.createdAt AS date) = :date " +
           "AND p.status = com.example.solimus.enums.PaymentStatus.COMPLETED")
    BigDecimal sumByProviderIdAndDate(
        @Param("providerId") Long providerId,
        @Param("date") LocalDate date);

    /**
     * Calcule le total des paiements validés reçus par un prestataire dans un intervalle de dates donné.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentProvider p " +
           "WHERE p.provider.id = :providerId " +
           "AND CAST(p.createdAt AS date) BETWEEN :startDate AND :endDate " +
           "AND p.status = com.example.solimus.enums.PaymentStatus.COMPLETED")
    BigDecimal sumByProviderIdBetween(
        @Param("providerId") Long providerId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * Récupère tous les paiements PENDING créés avant une certaine date
     * → utilisé par le scheduler pour expirer les paiements en attente trop anciens
     */
    List<PaymentProvider> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime dateTime);

    /**
     * Récupère un paiement par intervention et type
     * → utilisé pour vérifier si un paiement existe déjà et permettre de réinitier en cas d'échec
     */
    Optional<PaymentProvider> findByInterventionRequestIdAndType(Long requestId, PaymentType type);
}
