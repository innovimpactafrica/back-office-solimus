package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Fusionne les abonnements syndics et prestataires pour la page "Liste des abonnés"
public interface SubscriberRepository extends JpaRepository<SyndicSubscription, Long> {

    // Recherche paginée sur les 2 sources d'abonnements (Syndic + Prestataire),
    // avec recherche par nom/prénom/email, filtre par statut et par type d'abonné
    @Query(
        value =
                // Première partie : abonnements syndics
                "SELECT 'SYNDIC' AS subscriber_type, ss.id AS subscription_id, " +
                "       CONCAT(u.first_name, ' ', u.last_name) AS client_name, " +
                "       u.email AS client_email, u.city AS city, u.country AS country, " +
                "       sp.name AS plan_name, ss.amount_paid AS amount, " +
                "       ss.start_date AS start_date, ss.end_date AS end_date, ss.status AS status " +
                "FROM syndic_subscriptions ss " +
                "JOIN users u ON u.id = ss.syndic_id " +
                "JOIN syndic_plan sp ON sp.id = ss.syndic_plan_id " +
                "WHERE (:search IS NULL OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR ss.status = :status) " +
                "AND (:subscriberType IS NULL OR :subscriberType = 'SYNDIC') " +

                "UNION ALL " +

                // Deuxième partie : abonnements prestataires
                "SELECT 'PRESTATAIRE' AS subscriber_type, s.id AS subscription_id, " +
                "       CONCAT(u.first_name, ' ', u.last_name) AS client_name, " +
                "       u.email AS client_email, u.city AS city, u.country AS country, " +
                "       pp.name AS plan_name, s.amount_paid AS amount, " +
                "       s.start_date AS start_date, s.end_date AS end_date, s.status AS status " +
                "FROM subscriptions s " +
                "JOIN users u ON u.id = s.provider_id " +
                "JOIN provider_plan pp ON pp.id = s.provider_plan_id " +
                "WHERE (:search IS NULL OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "       OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR s.status = :status) " +
                "AND (:subscriberType IS NULL OR :subscriberType = 'PRESTATAIRE') " +

                // Les 2 sources fusionnées sont triées ensemble, du plus récent au plus ancien
                "ORDER BY start_date DESC",

        // Requête séparée pour compter le nombre total de résultats (nécessaire pour la pagination),
        // avec exactement les mêmes filtres que la requête principale
        countQuery =
                "SELECT COUNT(*) FROM (" +
                "  SELECT ss.id FROM syndic_subscriptions ss " +
                "  JOIN users u ON u.id = ss.syndic_id " +
                "  JOIN syndic_plan sp ON sp.id = ss.syndic_plan_id " +
                "  WHERE (:search IS NULL OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "         OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "         OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "  AND (:status IS NULL OR ss.status = :status) " +
                "  AND (:subscriberType IS NULL OR :subscriberType = 'SYNDIC') " +
                "  UNION ALL " +
                "  SELECT s.id FROM subscriptions s " +
                "  JOIN users u ON u.id = s.provider_id " +
                "  JOIN provider_plan pp ON pp.id = s.provider_plan_id " +
                "  WHERE (:search IS NULL OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "         OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "         OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "  AND (:status IS NULL OR s.status = :status) " +
                "  AND (:subscriberType IS NULL OR :subscriberType = 'PRESTATAIRE') " +
                ") AS combined",

        // Requête SQL brute (pas du JPQL) car UNION n'est pas supporté par Hibernate directement
        nativeQuery = true
    )
    // Renvoie une page de résultats bruts : chaque ligne est un tableau de colonnes, pas un objet Java
    Page<Object[]> searchSubscribers(@Param("search") String search,
                                       @Param("status") String status,
                                       @Param("subscriberType") String subscriberType,
                                       Pageable pageable);
}