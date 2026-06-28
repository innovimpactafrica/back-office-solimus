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


    // ============================================================
    // PRESTATAIRE
    // ============================================================

    // Récupère toutes les demandes d'intervention notifiées à ce prestataire,
    // mais qui ne lui sont pas (ou pas encore) définitivement assignées —
    // c'est-à-dire : soit personne n'a encore été choisi (selectedProvider = null),
    // soit un AUTRE prestataire a été choisi à sa place.
    // Sert à alimenter l'écran "Demandes" côté prestataire (pas l'écran "Travaux").
    @Query("SELECT i FROM InterventionRequest i " +
            "WHERE :provider MEMBER OF i.notifiedProviders " +
            "AND (i.selectedProvider IS NULL OR i.selectedProvider != :provider)")
    List<InterventionRequest> findNotifiedRequestsNotAssignedToMe(@Param("provider") User provider);

    // Cas SANS filtre : toutes les demandes notifiées, non assignées à moi,
    // peu importe leur statut calculé (REJECTED, QUOTE_SENT, ou PENDING_QUOTE)
    @Query("SELECT i FROM InterventionRequest i " +
            "WHERE :provider MEMBER OF i.notifiedProviders " +
            "AND (i.selectedProvider IS NULL OR i.selectedProvider != :provider) " +
            "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(i.residence.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InterventionRequest> findAllNotifiedRequests(
            @Param("provider") User provider,
            @Param("search") String search,
            Pageable pageable);

    // Cas REJECTED : un autre prestataire a été choisi (pas moi)
    @Query("SELECT i FROM InterventionRequest i " +
            "WHERE :provider MEMBER OF i.notifiedProviders " +
            "AND i.selectedProvider IS NOT NULL " +
            "AND i.selectedProvider != :provider " +
            "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(i.residence.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InterventionRequest> findRejectedRequests(
            @Param("provider") User provider,
            @Param("search") String search,
            Pageable pageable);

    // Cas QUOTE_SENT : personne choisi encore, ET j'ai déjà un devis sur cette demande
    @Query("SELECT i FROM InterventionRequest i " +
            "WHERE :provider MEMBER OF i.notifiedProviders " +
            "AND i.selectedProvider IS NULL " +
            "AND EXISTS (SELECT 1 FROM Quote q WHERE q.interventionRequest = i AND q.provider = :provider) " +
            "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(i.residence.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InterventionRequest> findQuoteSentRequests(
            @Param("provider") User provider,
            @Param("search") String search,
            Pageable pageable);

    // Cas PENDING_QUOTE : personne choisi encore, ET je n'ai PAS encore soumis de devis
    @Query("SELECT i FROM InterventionRequest i " +
            "WHERE :provider MEMBER OF i.notifiedProviders " +
            "AND i.selectedProvider IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM Quote q WHERE q.interventionRequest = i AND q.provider = :provider) " +
            "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(i.residence.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InterventionRequest> findPendingQuoteRequests(
            @Param("provider") User provider,
            @Param("search") String search,
            Pageable pageable);

    // On compte toutes les demandes notifiées à ce prestataire, non assignées à lui —
    // même critère que findAllNotifiedRequests, mais on ne veut que le total, pas les données
    @Query("SELECT COUNT(i) FROM InterventionRequest i " +
           "WHERE :provider MEMBER OF i.notifiedProviders " +
           "AND (i.selectedProvider IS NULL OR i.selectedProvider != :provider)")
    long countAllNotifiedRequests(@Param("provider") User provider);


    // Récupère une demande précise UNIQUEMENT si ce prestataire fait partie de la liste des notifiés
    Optional<InterventionRequest> findByIdAndNotifiedProvidersContaining(Long id, User provider);

    // ============================================================
    // OWNER
    // ============================================================
    // Variante avec filtre supplémentaire par résidence (residenceId optionnel)
    @Query("SELECT ir FROM InterventionRequest ir WHERE ir.owner = :owner " +
            "AND (:search IS NULL OR LOWER(ir.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR ir.status = :status) " +
            "AND (:residenceId IS NULL OR ir.residence.id = :residenceId) " +
            "ORDER BY ir.createdAt DESC")
    Page<InterventionRequest> findByOwnerWithFiltersAndResidence(
            @Param("owner") User owner,
            @Param("search") String search,
            @Param("status") InterventionStatus status,
            @Param("residenceId") Long residenceId,
          Pageable pageable);


    // Compter le total des interventions d'un copropriétaire (avec filtre optionnel par résidence)
    @Query("SELECT COUNT(ir) FROM InterventionRequest ir WHERE ir.owner = :owner " +
            "AND (:residenceId IS NULL OR ir.residence.id = :residenceId)")
    long countByOwner(@Param("owner") User owner, @Param("residenceId") Long residenceId);

    // Compter les interventions en cours (STARTED) d'un copropriétaire (avec filtre optionnel par résidence)
    @Query("SELECT COUNT(ir) FROM InterventionRequest ir WHERE ir.owner = :owner " +
            "AND ir.status = :status " +
            "AND (:residenceId IS NULL OR ir.residence.id = :residenceId)")
    long countByOwnerAndStatus(@Param("owner") User owner, @Param("status") InterventionStatus status, @Param("residenceId") Long residenceId);



    // Lister les demandes créées par un syndic précis
    List<InterventionRequest> findAllBySyndic(User syndic);

    // Lister toutes les demandes d'intervention des résidences d'un syndic
    // (créées par le syndic ou par les copropriétaires de ses résidences)
    @Query("SELECT ir FROM InterventionRequest ir WHERE ir.residence.syndic = :syndic")
    List<InterventionRequest> findAllByResidenceSyndic(@Param("syndic") User syndic);

    // Lister les demandes créées par un copropriétaire précis
    List<InterventionRequest> findAllByOwner(User owner);


    // Récupère une demande en la verrouillant en écriture jusqu'à la fin de la transaction.
    // Utilisé lors de l'acceptation d'un devis pour éviter deux validations concurrentes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ir FROM InterventionRequest ir WHERE ir.id = :id")
    Optional<InterventionRequest> findByIdForUpdate(@Param("id") Long id);
    
    // Lister les demandes assignées à un prestataire précis
    List<InterventionRequest> findAllBySelectedProvider(User provider);

    // Rechercher et filtrer les interventions assignées à un prestataire avec pagination
    @Query("SELECT ir FROM InterventionRequest ir WHERE ir.selectedProvider = :provider " +
           "AND (:search IS NULL OR LOWER(ir.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR ir.status = :status) " +
           "ORDER BY ir.createdAt DESC")
    org.springframework.data.domain.Page<InterventionRequest> findBySelectedProviderWithFilters(
            @Param("provider") User provider,
            @Param("search") String search,
            @Param("status") InterventionStatus status,
            org.springframework.data.domain.Pageable pageable);


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
