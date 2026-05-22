package com.example.solimus.repositories;

import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.User;
import com.example.solimus.enums.InterventionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterventionRequestRepository extends JpaRepository<InterventionRequest, Long> {
    
    // Lister les demandes créées par un syndic précis
    List<InterventionRequest> findAllBySyndic(User syndic);

    // Récupère une demande en la verrouillant en écriture jusqu'à la fin de la transaction.
    // Utilisé lors de l'acceptation d'un devis pour éviter deux validations concurrentes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ir FROM InterventionRequest ir WHERE ir.id = :id")
    Optional<InterventionRequest> findByIdForUpdate(@Param("id") Long id);
    
    // Lister les demandes assignées à un prestataire précis
    List<InterventionRequest> findAllBySelectedProvider(User provider);

    // Vérifier si une demande spécifique est accessible à un prestataire (si il a été notifié)
    Optional<InterventionRequest> findByIdAndNotifiedProvidersContaining(Long id, User provider);

    // ===== COMPTEURS POUR LE DASHBOARD PRESTATAIRE (DERIVED QUERIES) =====

    int countByNotifiedProvidersId(Long providerId);

    int countByNotifiedProvidersIdAndStatus(Long providerId, InterventionStatus status);

    int countBySelectedProviderIdAndStatus(Long providerId, InterventionStatus status);

    int countBySelectedProviderId(Long providerId);

    // ===== VARIATIONS POUR LE DASHBOARD (COMPARATIVE MONTHS) =====

    int countByNotifiedProvidersIdAndCreatedAtBetween(Long providerId, LocalDateTime startDate, LocalDateTime endDate);

    int countByNotifiedProvidersIdAndStatusAndCreatedAtBetween(Long providerId, InterventionStatus status, LocalDateTime startDate, LocalDateTime endDate);

    int countBySelectedProviderIdAndStatusAndCreatedAtBetween(Long providerId, InterventionStatus status, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Calcule le total restant à payer par les syndics pour ce prestataire.
     */
    @Query("SELECT COALESCE(SUM(ir.remainingAmount), 0) FROM InterventionRequest ir WHERE ir.selectedProvider.id = :providerId AND ir.remainingAmount > 0")
    BigDecimal sumRemainingAmountByProviderId(@Param("providerId") Long providerId);

    /**
     * Compte le nombre total de demandes reçues par un prestataire.
     */
    @Query("SELECT COUNT(ir) FROM InterventionRequest ir JOIN ir.notifiedProviders p WHERE p.id = :providerId")
    long countRequestsByProvider(@Param("providerId") Long providerId);

    /**
     * Recherche les demandes d'intervention (PENDING) proches d'un prestataire.
     * Basé sur la spécialité du prestataire et un rayon de 30km autour de sa position.
     */
    @Query(value = """
        SELECT ir.*,
               (6371 * ACOS(
                   COS(RADIANS(:providerLat)) * COS(RADIANS(res.latitude)) *
                   COS(RADIANS(res.longitude) - RADIANS(:providerLng)) +
                   SIN(RADIANS(:providerLat)) * SIN(RADIANS(res.latitude))
               )) AS distance_km
        FROM intervention_requests ir
        INNER JOIN residences res ON ir.residence_id = res.id
        WHERE ir.status = 'PENDING'
          AND ir.specialty_id = :specialtyId
          AND (6371 * ACOS(
                   COS(RADIANS(:providerLat)) * COS(RADIANS(res.latitude)) *
                   COS(RADIANS(res.longitude) - RADIANS(:providerLng)) +
                   SIN(RADIANS(:providerLat)) * SIN(RADIANS(res.latitude))
               )) <= :radiusKm
        ORDER BY ir.created_at DESC
        """, nativeQuery = true)
    List<InterventionRequest> findNearbyRequests(
        @Param("providerLat") double providerLat,
        @Param("providerLng") double providerLng,
        @Param("specialtyId") Long specialtyId,
        @Param("radiusKm") double radiusKm
    );

    /**
     * Listing filtré et paginé pour le dashboard prestataire.
     */
    @Query("SELECT ir FROM InterventionRequest ir " +
           "JOIN ir.notifiedProviders p " +
           "WHERE p.id = :providerId " +
           "AND (:status IS NULL OR ir.status = :status) " +
           "AND (:search IS NULL OR LOWER(ir.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(ir.residence.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY ir.createdAt DESC")
    Page<InterventionRequest> findFilteredRequests(
        @Param("providerId") Long providerId,
        @Param("search") String search,
        @Param("status") InterventionStatus status,
        Pageable pageable
    );
}
