package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Fusionne les demandes de retrait Syndic + Prestataire pour la liste "Admin > Demandes de retraits"
// (même principe que SubscriberRepository pour les abonnements) — chaque ligne est une demande
// distincte, pas de dédoublonnage par demandeur
public interface AdminWithdrawalRequestRepository extends JpaRepository<SyndicWithdrawalRequest, Long> {

    // Recherche paginée sur les 2 sources de demandes (Syndic + Prestataire), triée de la plus
    // récente à la plus ancienne. "search" filtre sur prénom/nom/raison sociale, "status" sur le
    // statut de la demande.
    @Query(
        value =
                "WITH syndic_wr AS (" +
                "  SELECT swr.id AS id, CONVERT('SYNDIC' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS type, " +
                "         CONVERT(COALESCE(spr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS company_name, " +
                "         CONVERT(u.first_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS first_name, " +
                "         CONVERT(u.last_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS last_name, " +
                "         CONVERT(spr.company_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS raw_company_name, " +
                "         swr.requested_at AS requested_at, swr.amount AS amount, " +
                "         CONVERT(swr.mode USING utf8mb4) COLLATE utf8mb4_unicode_ci AS mode_raw, " +
                "         CONVERT(swr.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status " +
                "  FROM syndic_withdrawal_requests swr " +
                "  JOIN syndic_wallets sw ON sw.id = swr.wallet_id " +
                "  JOIN users u ON u.id = sw.syndic_id " +
                "  LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "), " +
                "provider_wr AS (" +
                "  SELECT pwr.id AS id, CONVERT('PRESTATAIRE' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS type, " +
                "         CONVERT(COALESCE(ppr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS company_name, " +
                "         CONVERT(u.first_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS first_name, " +
                "         CONVERT(u.last_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS last_name, " +
                "         CONVERT(ppr.company_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS raw_company_name, " +
                "         pwr.created_at AS requested_at, pwr.amount AS amount, " +
                "         CONVERT(pwr.method USING utf8mb4) COLLATE utf8mb4_unicode_ci AS mode_raw, " +
                "         CONVERT(pwr.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status " +
                "  FROM withdrawal_requests pwr " +
                "  JOIN users u ON u.id = pwr.provider_id " +
                "  LEFT JOIN provider_profiles ppr ON ppr.user_id = u.id " +
                ") " +
                "SELECT id, type, company_name, requested_at, amount, mode_raw, status " +
                "FROM (SELECT * FROM syndic_wr UNION ALL SELECT * FROM provider_wr) combined " +
                "WHERE (:search IS NULL OR :search = '' " +
                "       OR LOWER(first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(raw_company_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR status = :status) " +
                "ORDER BY requested_at DESC",

        countQuery =
                "WITH syndic_wr AS (" +
                "  SELECT CONVERT(COALESCE(spr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS company_name, " +
                "         CONVERT(u.first_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS first_name, " +
                "         CONVERT(u.last_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS last_name, " +
                "         CONVERT(spr.company_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS raw_company_name, " +
                "         CONVERT(swr.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status " +
                "  FROM syndic_withdrawal_requests swr " +
                "  JOIN syndic_wallets sw ON sw.id = swr.wallet_id " +
                "  JOIN users u ON u.id = sw.syndic_id " +
                "  LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "), " +
                "provider_wr AS (" +
                "  SELECT CONVERT(COALESCE(ppr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS company_name, " +
                "         CONVERT(u.first_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS first_name, " +
                "         CONVERT(u.last_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS last_name, " +
                "         CONVERT(ppr.company_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS raw_company_name, " +
                "         CONVERT(pwr.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status " +
                "  FROM withdrawal_requests pwr " +
                "  JOIN users u ON u.id = pwr.provider_id " +
                "  LEFT JOIN provider_profiles ppr ON ppr.user_id = u.id " +
                ") " +
                "SELECT COUNT(*) FROM (SELECT * FROM syndic_wr UNION ALL SELECT * FROM provider_wr) combined " +
                "WHERE (:search IS NULL OR :search = '' " +
                "       OR LOWER(first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(raw_company_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR status = :status)",

        // Requête SQL brute (pas du JPQL) car UNION n'est pas supporté par Hibernate
        nativeQuery = true
    )
    Page<Object[]> searchWithdrawalRequests(@Param("search") String search,
                                             @Param("status") String status,
                                             Pageable pageable);
}