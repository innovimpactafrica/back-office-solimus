package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Fusionne les abonnements syndics et prestataires pour la page "Liste des abonnés"
public interface SubscriberRepository extends JpaRepository<SyndicSubscription, Long> {

    // Recherche paginée sur les 2 sources d'abonnements (Syndic + Prestataire) — une seule ligne
    // par abonné (son abonnement ACTIVE s'il y en a un, sinon le plus récemment créé sinon), pas
    // tout son historique de tentatives. Ne jamais choisir
    // "le plus récent" en ignorant le statut, sous peine de ramener une tentative échouée/annulée
    // plus récente à la place du véritable abonnement en cours.
    @Query(
        value =
                "WITH syndic_latest AS (" +
                "  SELECT CONVERT('SYNDIC' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subscriber_type, ss.id AS subscription_id, " +
                "         CONVERT(COALESCE(spr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_name, " +
                "         CONVERT(u.email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_email, " +
                "         CONVERT(u.city USING utf8mb4) COLLATE utf8mb4_unicode_ci AS city, " +
                "         CONVERT(u.country USING utf8mb4) COLLATE utf8mb4_unicode_ci AS country, " +
                "         CONVERT(sp.name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS plan_name, ss.amount_paid AS amount, " +
                "         ss.start_date AS start_date, ss.end_date AS end_date, " +
                "         CONVERT(ss.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY ss.syndic_id " +
                "             ORDER BY CASE WHEN ss.status = 'ACTIVE' THEN 0 ELSE 1 END, ss.created_at DESC" +
                "         ) AS rn " +
                "  FROM syndic_subscriptions ss " +
                "  JOIN users u ON u.id = ss.syndic_id " +
                "  JOIN syndic_plan sp ON sp.id = ss.syndic_plan_id " +
                "  LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "), " +
                "provider_latest AS (" +
                "  SELECT CONVERT('PRESTATAIRE' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subscriber_type, s.id AS subscription_id, " +
                "         CONVERT(COALESCE(ppr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_name, " +
                "         CONVERT(u.email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_email, " +
                "         CONVERT(u.city USING utf8mb4) COLLATE utf8mb4_unicode_ci AS city, " +
                "         CONVERT(u.country USING utf8mb4) COLLATE utf8mb4_unicode_ci AS country, " +
                "         CONVERT(pp.name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS plan_name, s.amount_paid AS amount, " +
                "         s.start_date AS start_date, s.end_date AS end_date, " +
                "         CONVERT(s.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY s.provider_id " +
                "             ORDER BY CASE WHEN s.status = 'ACTIVE' THEN 0 ELSE 1 END, s.created_at DESC" +
                "         ) AS rn " +
                "  FROM subscriptions s " +
                "  JOIN users u ON u.id = s.provider_id " +
                "  JOIN provider_plan pp ON pp.id = s.provider_plan_id " +
                "  LEFT JOIN provider_profiles ppr ON ppr.user_id = u.id " +
                ") " +
                "SELECT subscriber_type, subscription_id, client_name, client_email, city, country, " +
                "       plan_name, amount, start_date, end_date, status " +
                "FROM (" +
                "  SELECT * FROM syndic_latest WHERE rn = 1" +
                "  UNION ALL " +
                "  SELECT * FROM provider_latest WHERE rn = 1" +
                ") combined " +
                "WHERE (:search IS NULL OR LOWER(client_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(client_email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR status = :status) " +
                "AND (:subscriberType IS NULL OR subscriber_type = :subscriberType) " +
                "ORDER BY start_date DESC",

        // Requête séparée pour compter le nombre total de résultats (nécessaire pour la pagination),
        // avec exactement les mêmes filtres et le même dédoublonnage que la requête principale
        countQuery =
                "WITH syndic_latest AS (" +
                "  SELECT CONVERT('SYNDIC' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subscriber_type, " +
                "         CONVERT(COALESCE(spr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_name, " +
                "         CONVERT(u.email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_email, " +
                "         CONVERT(ss.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY ss.syndic_id " +
                "             ORDER BY CASE WHEN ss.status = 'ACTIVE' THEN 0 ELSE 1 END, ss.created_at DESC" +
                "         ) AS rn " +
                "  FROM syndic_subscriptions ss " +
                "  JOIN users u ON u.id = ss.syndic_id " +
                "  LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "), " +
                "provider_latest AS (" +
                "  SELECT CONVERT('PRESTATAIRE' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subscriber_type, " +
                "         CONVERT(COALESCE(ppr.company_name, CONCAT(u.first_name, ' ', u.last_name)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_name, " +
                "         CONVERT(u.email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS client_email, " +
                "         CONVERT(s.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY s.provider_id " +
                "             ORDER BY CASE WHEN s.status = 'ACTIVE' THEN 0 ELSE 1 END, s.created_at DESC" +
                "         ) AS rn " +
                "  FROM subscriptions s " +
                "  JOIN users u ON u.id = s.provider_id " +
                "  LEFT JOIN provider_profiles ppr ON ppr.user_id = u.id " +
                ") " +
                "SELECT COUNT(*) FROM (" +
                "  SELECT * FROM syndic_latest WHERE rn = 1" +
                "  UNION ALL " +
                "  SELECT * FROM provider_latest WHERE rn = 1" +
                ") combined " +
                "WHERE (:search IS NULL OR LOWER(client_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(client_email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR status = :status) " +
                "AND (:subscriberType IS NULL OR subscriber_type = :subscriberType)",

        // Requête SQL brute (pas du JPQL) car UNION et les fonctions fenêtre ne sont pas supportées par Hibernate
        nativeQuery = true
    )
    // Renvoie une page de résultats bruts : chaque ligne est un tableau de colonnes, pas un objet Java
    Page<Object[]> searchSubscribers(@Param("search") String search,
                                       @Param("status") String status,
                                       @Param("subscriberType") String subscriberType,
                                       Pageable pageable);
}