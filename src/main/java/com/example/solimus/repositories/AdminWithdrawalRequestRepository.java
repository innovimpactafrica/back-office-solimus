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
                "  SELECT swr.id AS id, CAST('SYNDIC' AS VARCHAR) AS type, " +
                "         CAST(COALESCE(spr.company_name, CONCAT(u.first_name, ' ', u.last_name)) AS VARCHAR) AS company_name, " +
                "         CAST(u.first_name AS VARCHAR) AS first_name, " +
                "         CAST(u.last_name AS VARCHAR) AS last_name, " +
                "         CAST(spr.company_name AS VARCHAR) AS raw_company_name, " +
                "         swr.requested_at AS requested_at, swr.amount AS amount, " +
                "         CAST(swr.mode AS VARCHAR) AS mode_raw, " +
                "         CAST(swr.status AS VARCHAR) AS status " +
                "  FROM syndic_withdrawal_requests swr " +
                "  JOIN syndic_wallets sw ON sw.id = swr.wallet_id " +
                "  JOIN users u ON u.id = sw.syndic_id " +
                "  LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "), " +
                "provider_wr AS (" +
                "  SELECT pwr.id AS id, CAST('PRESTATAIRE' AS VARCHAR) AS type, " +
                "         CAST(COALESCE(ppr.company_name, CONCAT(u.first_name, ' ', u.last_name)) AS VARCHAR) AS company_name, " +
                "         CAST(u.first_name AS VARCHAR) AS first_name, " +
                "         CAST(u.last_name AS VARCHAR) AS last_name, " +
                "         CAST(ppr.company_name AS VARCHAR) AS raw_company_name, " +
                "         pwr.created_at AS requested_at, pwr.amount AS amount, " +
                "         CAST(pwr.method AS VARCHAR) AS mode_raw, " +
                "         CAST(pwr.status AS VARCHAR) AS status " +
                "  FROM withdrawal_requests pwr " +
                "  JOIN users u ON u.id = pwr.provider_id " +
                "  LEFT JOIN provider_profiles ppr ON ppr.user_id = u.id " +
                ") " +
                "SELECT id, type, company_name, requested_at, amount, mode_raw, status " +
                "FROM (SELECT * FROM syndic_wr UNION ALL SELECT * FROM provider_wr) combined " +
                "WHERE (CAST(:search AS VARCHAR) IS NULL OR CAST(:search AS VARCHAR) = '' " +
                "       OR LOWER(first_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
                "       OR LOWER(last_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
                "       OR LOWER(raw_company_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
                "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR)) " +
                "ORDER BY requested_at DESC",

        countQuery =
                "WITH syndic_wr AS (" +
                "  SELECT CAST(COALESCE(spr.company_name, CONCAT(u.first_name, ' ', u.last_name)) AS VARCHAR) AS company_name, " +
                "         CAST(u.first_name AS VARCHAR) AS first_name, " +
                "         CAST(u.last_name AS VARCHAR) AS last_name, " +
                "         CAST(spr.company_name AS VARCHAR) AS raw_company_name, " +
                "         CAST(swr.status AS VARCHAR) AS status " +
                "  FROM syndic_withdrawal_requests swr " +
                "  JOIN syndic_wallets sw ON sw.id = swr.wallet_id " +
                "  JOIN users u ON u.id = sw.syndic_id " +
                "  LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "), " +
                "provider_wr AS (" +
                "  SELECT CAST(COALESCE(ppr.company_name, CONCAT(u.first_name, ' ', u.last_name)) AS VARCHAR) AS company_name, " +
                "         CAST(u.first_name AS VARCHAR) AS first_name, " +
                "         CAST(u.last_name AS VARCHAR) AS last_name, " +
                "         CAST(ppr.company_name AS VARCHAR) AS raw_company_name, " +
                "         CAST(pwr.status AS VARCHAR) AS status " +
                "  FROM withdrawal_requests pwr " +
                "  JOIN users u ON u.id = pwr.provider_id " +
                "  LEFT JOIN provider_profiles ppr ON ppr.user_id = u.id " +
                ") " +
                "SELECT COUNT(*) FROM (SELECT * FROM syndic_wr UNION ALL SELECT * FROM provider_wr) combined " +
                "WHERE (CAST(:search AS VARCHAR) IS NULL OR CAST(:search AS VARCHAR) = '' " +
                "       OR LOWER(first_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
                "       OR LOWER(last_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
                "       OR LOWER(raw_company_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
                "AND (CAST(:status AS VARCHAR) IS NULL OR status = CAST(:status AS VARCHAR))",

        // Requête SQL brute (pas du JPQL) car UNION n'est pas supporté par Hibernate
        nativeQuery = true
    )
    Page<Object[]> searchWithdrawalRequests(@Param("search") String search,
                                             @Param("status") String status,
                                             Pageable pageable);
}