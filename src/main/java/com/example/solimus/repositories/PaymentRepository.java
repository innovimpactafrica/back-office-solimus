package com.example.solimus.repositories;

import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    // Même recherche, ligne verrouillée jusqu'à la fin de la transaction — utilisée par le callback
    // TouchPay : deux callbacks rejoués en parallèle ne peuvent pas créditer deux fois le prestataire
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentProvider p WHERE p.reference = :reference")
    Optional<PaymentProvider> findByReferenceForUpdate(@Param("reference") String reference);

    /**
     * Récupère tous les paiements d'un prestataire triés par date décroissante
     */
    List<PaymentProvider> findAllByProviderIdOrderByCreatedAtDesc(Long providerId);

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
     * Historique fusionné paiements + retraits + recharges + commissions d'un prestataire ("Mon Wallet" → onglet Transactions),
     * paginé directement en base via UNION ALL natif
     */
    // Chaque colonne texte est typée VARCHAR dans toutes les branches : le UNION ALL reste homogène.
    @Query(value =
            "SELECT * FROM ( " +
            "  SELECT " +
            "    CAST(CONCAT(COALESCE(r.name, 'Résidence'), ' - ', COALESCE(s.name, 'Intervention')) AS VARCHAR) AS label, " +
            "    p.amount AS amount, " +
            "    CAST('ENTREE' AS VARCHAR) AS type, " +
            "    CAST(CASE WHEN p.status = 'COMPLETED' THEN 'Reçu' ELSE 'En attente' END AS VARCHAR) AS status, " +
            "    p.created_at AS transaction_date " +
            "  FROM payments p " +
            "  LEFT JOIN intervention_requests ir ON ir.id = p.intervention_request_id " +
            "  LEFT JOIN residences r ON r.id = ir.residence_id " +
            "  LEFT JOIN specialties s ON s.id = ir.specialty_id " +
            "  WHERE p.provider_id = :providerId " +
            "  UNION ALL " +
            "  SELECT " +
            "    CAST(CONCAT('Retrait ', COALESCE(w.method, 'N/A')) AS VARCHAR) AS label, " +
            "    -w.amount AS amount, " +
            "    CAST('SORTIE' AS VARCHAR) AS type, " +
            "    CAST(CASE WHEN w.status = 'COMPLETED' THEN 'Effectué' " +
            "         WHEN w.status = 'REJECTED' THEN 'Refusé' " +
            "         ELSE 'En attente' END AS VARCHAR) AS status, " +
            "    w.created_at AS transaction_date " +
            "  FROM withdrawal_requests w " +
            "  WHERE w.provider_id = :providerId " +
            "  UNION ALL " +
            // Recharges du compte (preuve envoyée ou crédit direct de l'admin)
            "  SELECT " +
            "    CAST(CONCAT('Recharge du compte ', COALESCE(rc.reference, '')) AS VARCHAR) AS label, " +
            "    rc.amount AS amount, " +
            "    CAST('ENTREE' AS VARCHAR) AS type, " +
            "    CAST(CASE WHEN rc.status = 'VALIDATED' THEN 'Reçu' " +
            "         WHEN rc.status = 'REJECTED' THEN 'Refusé' " +
            "         ELSE 'En attente' END AS VARCHAR) AS status, " +
            "    rc.submitted_at AS transaction_date " +
            "  FROM provider_wallet_recharges rc " +
            "  WHERE rc.provider_id = :providerId " +
            "  UNION ALL " +
            // Commissions Solimus prélevées sur les travaux
            "  SELECT " +
            "    CAST(CONCAT('Commission Solimus ', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(pc.commission_rate AS VARCHAR))), ' % — ', " +
            "           COALESCE(pir.title, 'Travaux')) AS VARCHAR) AS label, " +
            "    -pc.amount AS amount, " +
            "    CAST('SORTIE' AS VARCHAR) AS type, " +
            "    CAST('Effectué' AS VARCHAR) AS status, " +
            "    pc.transaction_date AS transaction_date " +
            "  FROM platform_wallet_transactions pc " +
            "  LEFT JOIN intervention_requests pir ON pir.id = pc.intervention_request_id " +
            "  WHERE pc.provider_id = :providerId " +
            ") AS combined " +
            "ORDER BY transaction_date DESC",
            countQuery =
            "SELECT (SELECT COUNT(*) FROM payments p WHERE p.provider_id = :providerId) " +
            "     + (SELECT COUNT(*) FROM withdrawal_requests w WHERE w.provider_id = :providerId) " +
            "     + (SELECT COUNT(*) FROM provider_wallet_recharges rc WHERE rc.provider_id = :providerId) " +
            "     + (SELECT COUNT(*) FROM platform_wallet_transactions pc WHERE pc.provider_id = :providerId)",
            nativeQuery = true)
    Page<Object[]> findProviderWalletTransactionsUnion(@Param("providerId") Long providerId, Pageable pageable);
}
