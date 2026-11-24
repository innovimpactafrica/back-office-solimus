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
    // Collation forcée sur chaque colonne texte : les tables anciennes sont en utf8mb4_unicode_ci, les
    // tables créées depuis par Hibernate prennent la collation par défaut de la base (utf8mb4_0900_ai_ci),
    // et MySQL refuse un UNION entre collations différentes ("Illegal mix of collations").
    @Query(value =
            "SELECT * FROM ( " +
            "  SELECT " +
            "    CONVERT(CONCAT(COALESCE(r.name, 'Résidence'), ' - ', COALESCE(s.name, 'Intervention')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS label, " +
            "    p.amount AS amount, " +
            "    CONVERT('ENTREE' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS type, " +
            "    CONVERT(CASE WHEN p.status = 'COMPLETED' THEN 'Reçu' ELSE 'En attente' END USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
            "    p.created_at AS transaction_date " +
            "  FROM payments p " +
            "  LEFT JOIN intervention_requests ir ON ir.id = p.intervention_request_id " +
            "  LEFT JOIN residences r ON r.id = ir.residence_id " +
            "  LEFT JOIN specialties s ON s.id = ir.specialty_id " +
            "  WHERE p.provider_id = :providerId " +
            "  UNION ALL " +
            "  SELECT " +
            "    CONVERT(CONCAT('Retrait ', COALESCE(w.method, 'N/A')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS label, " +
            "    -w.amount AS amount, " +
            "    CONVERT('SORTIE' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS type, " +
            "    CONVERT(CASE WHEN w.status = 'COMPLETED' THEN 'Effectué' " +
            "         WHEN w.status = 'REJECTED' THEN 'Refusé' " +
            "         ELSE 'En attente' END USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
            "    w.created_at AS transaction_date " +
            "  FROM withdrawal_requests w " +
            "  WHERE w.provider_id = :providerId " +
            "  UNION ALL " +
            // Recharges du compte (preuve envoyée ou crédit direct de l'admin)
            "  SELECT " +
            "    CONVERT(CONCAT('Recharge du compte ', COALESCE(rc.reference, '')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS label, " +
            "    rc.amount AS amount, " +
            "    CONVERT('ENTREE' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS type, " +
            "    CONVERT(CASE WHEN rc.status = 'VALIDATED' THEN 'Reçu' " +
            "         WHEN rc.status = 'REJECTED' THEN 'Refusé' " +
            "         ELSE 'En attente' END USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
            "    rc.submitted_at AS transaction_date " +
            "  FROM provider_wallet_recharges rc " +
            "  WHERE rc.provider_id = :providerId " +
            "  UNION ALL " +
            // Commissions Solimus prélevées sur les travaux
            "  SELECT " +
            "    CONVERT(CONCAT('Commission Solimus ', TRIM(TRAILING '.00' FROM CAST(pc.commission_rate AS CHAR)), ' % — ', " +
            "           COALESCE(pir.title, 'Travaux')) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS label, " +
            "    -pc.amount AS amount, " +
            "    CONVERT('SORTIE' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS type, " +
            "    CONVERT('Effectué' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
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
