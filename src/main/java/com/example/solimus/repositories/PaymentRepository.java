package com.example.solimus.repositories;

import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Historique fusionné paiements + retraits d'un prestataire ("Mon Wallet" → onglet Transactions),
     * paginé directement en base via UNION ALL natif
     */
    @Query(value =
            "SELECT * FROM ( " +
            "  SELECT " +
            "    CONCAT(COALESCE(r.name, 'Résidence'), ' - ', COALESCE(s.name, 'Intervention')) AS label, " +
            "    p.amount AS amount, " +
            "    'ENTREE' AS type, " +
            "    CASE WHEN p.status = 'COMPLETED' THEN 'Reçu' ELSE 'En attente' END AS status, " +
            "    p.created_at AS transaction_date " +
            "  FROM payments p " +
            "  LEFT JOIN intervention_requests ir ON ir.id = p.intervention_request_id " +
            "  LEFT JOIN residences r ON r.id = ir.residence_id " +
            "  LEFT JOIN specialties s ON s.id = ir.specialty_id " +
            "  WHERE p.provider_id = :providerId " +
            "  UNION ALL " +
            "  SELECT " +
            "    CONCAT('Retrait ', COALESCE(w.method, 'N/A')) AS label, " +
            "    -w.amount AS amount, " +
            "    'SORTIE' AS type, " +
            "    CASE WHEN w.status = 'COMPLETED' THEN 'Effectué' " +
            "         WHEN w.status = 'REJECTED' THEN 'Refusé' " +
            "         ELSE 'En attente' END AS status, " +
            "    w.created_at AS transaction_date " +
            "  FROM withdrawal_requests w " +
            "  WHERE w.provider_id = :providerId " +
            ") AS combined " +
            "ORDER BY transaction_date DESC",
            countQuery =
            "SELECT (SELECT COUNT(*) FROM payments p WHERE p.provider_id = :providerId) " +
            "     + (SELECT COUNT(*) FROM withdrawal_requests w WHERE w.provider_id = :providerId)",
            nativeQuery = true)
    Page<Object[]> findProviderWalletTransactionsUnion(@Param("providerId") Long providerId, Pageable pageable);
}
