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
    List<User> searchCoOwners(@Param("q") String q, Pageable pageable);


}
