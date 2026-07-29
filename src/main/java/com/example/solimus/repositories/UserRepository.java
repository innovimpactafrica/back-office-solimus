package com.example.solimus.repositories;

import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    
    Optional<User> findByPhone(String phone);

    Boolean existsByEmail(String email);

    Boolean existsByPhone(String phone);

    Boolean existsByEmailAndIdNot( String email,Long id);

    Boolean existsByPhoneAndIdNot(String phone, Long id);

    Boolean existsByRole_Name(ERole roleName);

    Boolean existsByEmailOrPhone(String email, String phone);

    /**
     * Recherche les prestataires proches d'une position GPS donnée.
     * Utilise la formule Haversine en SQL natif.
     *
     * On parcourt tous les prestataires actifs et
     * on filtre par spécialité et distance ( vérifie si leurs coordonnées (latitude/longitude) sont à moins de 30km des coordonnées de la résidence.)
     */
    @Query(value = """
        SELECT u.*, 
               (6371 * ACOS(
                   COS(RADIANS(:lat)) * COS(RADIANS(u.latitude)) * 
                   COS(RADIANS(u.longitude) - RADIANS(:lng)) + 
                   SIN(RADIANS(:lat)) * SIN(RADIANS(u.latitude))
               )) AS distance 
        FROM users u 
        INNER JOIN roles r ON u.role_id = r.id 
        LEFT JOIN subscriptions s ON u.id = s.provider_id 
        WHERE r.name = 'ROLE_PRESTATAIRE' 
          AND u.status = 'ACTIVE' -- Seuls les prestataires actifs
          AND u.specialty_id = :specialtyId
          AND (6371 * ACOS(
                   COS(RADIANS(:lat)) * COS(RADIANS(u.latitude)) * 
                   COS(RADIANS(u.longitude) - RADIANS(:lng)) + 
                   SIN(RADIANS(:lat)) * SIN(RADIANS(u.latitude))
               )) <= :radiusKm
        ORDER BY 
            CASE WHEN s.plan = 'PREMIUM' AND s.status = 'ACTIVE' THEN 0 ELSE 1 END ASC,
            distance ASC
        """, nativeQuery = true)
    List<User> findNearbyProviders(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("specialtyId") Long specialtyId,
            @Param("radiusKm") double radiusKm
    );

    /**
     * Listing général des utilisateurs avec filtres (utilisé par l'Admin).
     */
    @Query("SELECT u FROM User u " +
           "WHERE (:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:role IS NULL OR u.role.name = :role) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "AND u.role.name NOT IN ('ROLE_PRESTATAIRE', 'ROLE_COPROPRIETAIRE')")
    Page<User> findAllWithFilters(
            @Param("search") String search,
            @Param("role") ERole role,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    /**
     * Autocomplete — recherche un copropriétaire par prénom, nom, email ou téléphone.
     * Retourne les 5 premiers résultats pour ne pas surcharger l'affichage.
     */
    @Query("SELECT u FROM User u " +
           "WHERE u.role.name = 'ROLE_COPROPRIETAIRE' " +
           "AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR u.phone LIKE CONCAT('%', :q, '%'))")
    Page<User> searchCoOwners(@Param("q") String q, Pageable pageable);

    // ============================================================
    // ADMIN — GESTION DES SYNDICS (KPIs dashboard)
    // ============================================================

    // Compte tous les comptes ayant ce rôle, peu importe leur statut — "Total syndics"
    long countByRole_Name(ERole roleName);

    // Compte les comptes ayant ce rôle ET ce statut précis — "Syndics actifs" / "Syndics suspendus"
    long countByRole_NameAndStatus(ERole roleName, UserStatus status);

    // Compte combien de comptes de ce rôle étaient déjà inscrits avant une date donnée — utilisé
    // pour calculer l'évolution du nombre total de syndics (comparaison à 30 jours)
    long countByRole_NameAndCreatedAtBefore(ERole roleName, LocalDateTime dateTime);

    // Recherche paginée des syndics avec leur abonnement en cours (ACTIVE s'il existe, sinon le plus
    // récent — même règle que partout ailleurs dans l'admin), leur nombre de résidences et de
    // copropriétaires. Alimente la liste "Admin > Syndics".
    @Query(
        value =
                "WITH syndic_latest AS (" +
                "  SELECT ss.syndic_id, ss.syndic_plan_id, ss.end_date, ss.status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY ss.syndic_id " +
                "             ORDER BY CASE WHEN ss.status = 'ACTIVE' THEN 0 ELSE 1 END, ss.created_at DESC" +
                "         ) AS rn " +
                "  FROM syndic_subscriptions ss" +
                ") " +
                "SELECT u.id AS user_id, u.first_name AS first_name, u.last_name AS last_name, " +
                "       u.email AS email, " +
                "       CONVERT(spr.company_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS company_name, " +
                "       (SELECT COUNT(*) FROM residences r WHERE r.syndic_id = u.id) AS residences_count, " +
                "       (SELECT COUNT(*) FROM syndic_coowner_relation scr WHERE scr.syndic_id = u.id) AS coowners_count, " +
                "       CONVERT(sp.name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS plan_name, " +
                "       sl.end_date AS expiration_date, " +
                "       CONVERT(sl.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subscription_status, " +
                "       u.phone AS phone " +
                "FROM users u " +
                "JOIN roles ro ON ro.id = u.role_id " +
                "LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "LEFT JOIN syndic_latest sl ON sl.syndic_id = u.id AND sl.rn = 1 " +
                "LEFT JOIN syndic_plan sp ON sp.id = sl.syndic_plan_id " +
                "WHERE ro.name = 'ROLE_SYNDIC' " +
                "AND (:search IS NULL OR :search = '' " +
                "     OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(spr.company_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR sl.status = :status) " +
                "ORDER BY u.created_at DESC",

        countQuery =
                "WITH syndic_latest AS (" +
                "  SELECT ss.syndic_id, ss.status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY ss.syndic_id " +
                "             ORDER BY CASE WHEN ss.status = 'ACTIVE' THEN 0 ELSE 1 END, ss.created_at DESC" +
                "         ) AS rn " +
                "  FROM syndic_subscriptions ss" +
                ") " +
                "SELECT COUNT(*) " +
                "FROM users u " +
                "JOIN roles ro ON ro.id = u.role_id " +
                "LEFT JOIN syndic_profiles spr ON spr.user_id = u.id " +
                "LEFT JOIN syndic_latest sl ON sl.syndic_id = u.id AND sl.rn = 1 " +
                "WHERE ro.name = 'ROLE_SYNDIC' " +
                "AND (:search IS NULL OR :search = '' " +
                "     OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(spr.company_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR sl.status = :status)",

        // Requête SQL brute (pas du JPQL) car ROW_NUMBER() n'est pas supporté par Hibernate
        nativeQuery = true
    )
    Page<Object[]> searchSyndics(@Param("search") String search,
                                  @Param("status") String status,
                                  Pageable pageable);

    // ============================================================
    // ADMIN — GESTION DES PRESTATAIRES (liste paginée)
    // ============================================================

    // Recherche paginée des prestataires avec leur abonnement en cours (ACTIVE s'il existe, sinon le
    // plus récent), leur spécialité et leur nombre de missions terminées. Alimente la liste
    // "Admin > Prestataires". Même structure que searchSyndics, appliquée aux tables prestataire.
    @Query(
        value =
                "WITH provider_latest AS (" +
                "  SELECT s.provider_id, s.provider_plan_id, s.end_date, s.status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY s.provider_id " +
                "             ORDER BY CASE WHEN s.status = 'ACTIVE' THEN 0 ELSE 1 END, s.created_at DESC" +
                "         ) AS rn " +
                "  FROM subscriptions s" +
                ") " +
                "SELECT u.id AS user_id, u.first_name AS first_name, u.last_name AS last_name, " +
                "       u.email AS email, u.phone AS phone, u.city AS city, u.country AS country, " +
                "       CONVERT(pp.company_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS company_name, " +
                "       CONVERT(sp.name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS specialty_name, " +
                "       (SELECT COUNT(*) FROM intervention_requests ir WHERE ir.selected_provider_id = u.id " +
                "            AND ir.status IN ('FINISHED', 'FINAL_VALIDATION')) AS missions_count, " +
                "       CONVERT(plan.name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS plan_name, " +
                "       pl.end_date AS expiration_date, " +
                "       CONVERT(pl.status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subscription_status " +
                "FROM users u " +
                "JOIN roles ro ON ro.id = u.role_id " +
                "LEFT JOIN provider_profiles pp ON pp.user_id = u.id " +
                "LEFT JOIN specialties sp ON sp.id = pp.specialty_id " +
                "LEFT JOIN provider_latest pl ON pl.provider_id = u.id AND pl.rn = 1 " +
                "LEFT JOIN provider_plan plan ON plan.id = pl.provider_plan_id " +
                "WHERE ro.name = 'ROLE_PRESTATAIRE' " +
                "AND (:search IS NULL OR :search = '' " +
                "     OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(pp.company_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR pl.status = :status) " +
                "ORDER BY u.created_at DESC",

        countQuery =
                "WITH provider_latest AS (" +
                "  SELECT s.provider_id, s.status, " +
                "         ROW_NUMBER() OVER (" +
                "             PARTITION BY s.provider_id " +
                "             ORDER BY CASE WHEN s.status = 'ACTIVE' THEN 0 ELSE 1 END, s.created_at DESC" +
                "         ) AS rn " +
                "  FROM subscriptions s" +
                ") " +
                "SELECT COUNT(*) " +
                "FROM users u " +
                "JOIN roles ro ON ro.id = u.role_id " +
                "LEFT JOIN provider_profiles pp ON pp.user_id = u.id " +
                "LEFT JOIN provider_latest pl ON pl.provider_id = u.id AND pl.rn = 1 " +
                "WHERE ro.name = 'ROLE_PRESTATAIRE' " +
                "AND (:search IS NULL OR :search = '' " +
                "     OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
                "     OR LOWER(pp.company_name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                "AND (:status IS NULL OR pl.status = :status)",

        // Requête SQL brute (pas du JPQL) car ROW_NUMBER() n'est pas supporté par Hibernate
        nativeQuery = true
    )
    Page<Object[]> searchProviders(@Param("search") String search,
                                    @Param("status") String status,
                                    Pageable pageable);

    // ============================================================
    // ADMIN — DASHBOARD GLOBAL (activité récente plateforme)
    // ============================================================

    // Les N derniers comptes inscrits pour un rôle donné, du plus récent au plus ancien — utilisé
    // pour assembler le flux "Activité récente" du dashboard admin (prestataires validés, nouveaux
    // copropriétaires)
    List<User> findByRole_NameOrderByCreatedAtDesc(ERole roleName, Pageable pageable);
}
