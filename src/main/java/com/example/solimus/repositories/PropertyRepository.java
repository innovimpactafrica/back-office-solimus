package com.example.solimus.repositories;

import com.example.solimus.entities.Property;
import com.example.solimus.enums.PropertyRentalStatus;
import com.example.solimus.enums.PropertyStatus;
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
public interface PropertyRepository extends JpaRepository<Property, Long> {

    // Lister les biens d'une résidence
    List<Property> findByResidenceId(Long residenceId);

    // Récupère le bien loué par ce locataire — un locataire n'a jamais qu'un seul bien
    Optional<Property> findByTenantId(Long tenantId);

    // Lots dont le propriétaire a au moins un ChargeCallItem impayé (PENDING/PARTIALLY_PAID) — utilisé
    // par le job quotidien de rattrapage du displayStatus
    @Query("SELECT DISTINCT p FROM Property p, ChargeCallItem cci " +
           "WHERE p.owner = cci.coOwner AND p.residence = cci.chargeCall.budget.residence " +
           "AND cci.status IN ('PENDING', 'PARTIALLY_PAID')")
    List<Property> findPropertiesWithUnpaidCharges();

    // Lister les biens d'une résidence (paginé)
    Page<Property> findByResidenceId(Long residenceId, Pageable pageable);

    // Lister les biens d'un propriétaire dans une résidence spécifique
    List<Property> findByOwnerIdAndResidenceId(Long ownerId, Long residenceId);

    // Vérifier si un propriétaire a au moins un bien dans une résidence
    boolean existsByOwnerIdAndResidenceId(Long ownerId, Long residenceId);

    // Vérifier qu'un bien précis appartient à un propriétaire dans une résidence donnée
    boolean existsByIdAndOwnerIdAndResidenceId(Long id, Long ownerId, Long residenceId);

    // Vérifier si une référence existe déjà pour une résidence
    boolean existsByReferenceAndResidenceId(String reference, Long residenceId);

    // Compter les biens d'une résidence
    long countByResidenceId(Long residenceId);

    // Lister les biens d'une résidence qui ont un propriétaire
    List<Property> findByResidenceIdAndOwnerIsNotNull(Long residenceId);

    // Lister les biens d'une résidence par statut
    Page<Property> findByResidenceIdAndStatus(Long residenceId, PropertyStatus status, Pageable pageable);

    // Compter les biens d'une résidence ayant un statut précis (ex: OCCUPIED) — utilisé pour le taux d'occupation
    long countByResidenceIdAndStatus(Long residenceId, PropertyStatus status);

    // Répartition des biens d'une résidence par type (id du type, nom du type, nombre de biens)
    @Query("SELECT p.typeBien.id, p.typeBien.name, COUNT(p) FROM Property p " +
           "WHERE p.residence.id = :residenceId GROUP BY p.typeBien.id, p.typeBien.name")
    List<Object[]> countByResidenceIdGroupByPropertyType(@Param("residenceId") Long residenceId);

    // Lister tous les biens d'un propriétaire donné
    List<Property> findAllByOwnerId(Long ownerId);

    // Trouver le premier bien d'un propriétaire donné
    Optional<Property> findFirstByOwnerId(Long ownerId);

    // Compter les biens d'un propriétaire donné
    long countByOwnerId(Long ownerId);

    // Lister les biens occupés des résidences d'un syndic (pour lister les copropriétaires)
    List<Property> findByResidence_SyndicIdAndOwnerIsNotNull(Long syndicId);

    // Compter les propriétaires distincts d'une résidence
    @Query("SELECT COUNT(DISTINCT p.owner) FROM Property p WHERE p.residence.id = :residenceId AND p.owner IS NOT NULL")
    long countDistinctOwnersByResidenceId(@Param("residenceId") Long residenceId);

    // Copropriétaires distincts d'une résidence (évite les doublons si plusieurs lots)
    @Query("SELECT DISTINCT p.owner FROM Property p " +
           "WHERE p.residence.id = :residenceId AND p.owner IS NOT NULL")
    List<com.example.solimus.entities.User> findDistinctOwnersByResidenceId(@Param("residenceId") Long residenceId);

    // Compter les biens d'un syndic (toutes résidences confondues)
    @Query("SELECT COUNT(p) FROM Property p WHERE p.residence.syndic.id = :syndicId")
    long countByResidenceSyndicId(@Param("syndicId") Long syndicId);

    // Lister les biens d'une résidence avec filtres (paginé)
    // search : reference du lot OU nom du owner, LIKE insensible casse
    // floor : exact match
    // status : filtre calculé à la volée sur la présence propriétaire/locataire (VACANT/RENTED/TO_RENT),
    // pas le displayStatus (MAINTENANCE/UNPAID/LATE...) utilisé ailleurs dans l'app
    @Query("SELECT p FROM Property p LEFT JOIN p.owner o WHERE p.residence.id = :residenceId " +
           "AND (:search IS NULL OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR (o IS NOT NULL AND LOWER(o.firstName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "OR (o IS NOT NULL AND LOWER(o.lastName) LIKE LOWER(CONCAT('%', :search, '%')))) " +
           "AND (:floor IS NULL OR p.floor = :floor) " +
           "AND (:statusName IS NULL " +
           "     OR (:statusName = 'VACANT' AND p.owner IS NULL AND p.tenant IS NULL) " +
           "     OR (:statusName = 'RENTED' AND p.owner IS NOT NULL AND p.tenant IS NOT NULL) " +
           "     OR (:statusName = 'TO_RENT' AND p.owner IS NOT NULL AND p.tenant IS NULL)) " +
           "ORDER BY p.createdAt DESC")
    Page<Property> findByResidenceIdWithFilters(
            @Param("residenceId") Long residenceId,
            @Param("search") String search,
            @Param("floor") Integer floor,
            @Param("statusName") String statusName,
            Pageable pageable);

    // Trouver la date de première acquisition d'un copropriétaire chez un syndic
    // MIN(assignedAt) restreint au syndic
    @Query("SELECT MIN(p.assignedAt) FROM Property p " +
           "WHERE p.owner.id = :coOwnerId " +
           "AND p.residence.syndic.id = :syndicId " +
           "AND p.assignedAt IS NOT NULL")
    Optional<LocalDateTime> findFirstAcquisitionDateByCoOwnerAndSyndic(
            @Param("coOwnerId") Long coOwnerId,
            @Param("syndicId") Long syndicId);

    // Compter les appartements (lots) d'un copropriétaire, restreint aux résidences du syndic
    @Query("SELECT COUNT(p) FROM Property p " +
           "WHERE p.owner.id = :coOwnerId " +
           "AND p.residence.syndic.id = :syndicId")
    long countApartmentsByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // Compter les résidences distinctes d'un copropriétaire, restreint au syndic
    @Query("SELECT COUNT(DISTINCT p.residence.id) FROM Property p " +
           "WHERE p.owner.id = :coOwnerId " +
           "AND p.residence.syndic.id = :syndicId")
    long countResidencesByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // Calculer la somme des superficies des lots d'une résidence
    @Query("SELECT COALESCE(SUM(p.area), 0) FROM Property p WHERE p.residence.id = :residenceId")
    java.math.BigDecimal sumAreaByResidenceId(@Param("residenceId") Long residenceId);

    // Lister les biens d'un propriétaire dans les résidences d'un syndic
    List<Property> findByOwnerIdAndResidenceSyndicId(Long ownerId, Long syndicId);

    // Récupérer les résidences distinctes d'un copropriétaire pour un syndic donné (paginé)
    // Cette requête utilise DISTINCT pour éviter les doublons quand un copropriétaire a plusieurs lots dans la même résidence
    // La pagination est gérée directement par la base de données pour de meilleures performances
    @Query("SELECT DISTINCT p.residence FROM Property p " +
           "WHERE p.owner.id = :coOwnerId " +
           "AND p.residence.syndic.id = :syndicId " +
           "ORDER BY p.residence.name ASC")
    Page<com.example.solimus.entities.Residence> findDistinctResidencesByCoOwnerAndSyndic(
            @Param("coOwnerId") Long coOwnerId,
            @Param("syndicId") Long syndicId,
            Pageable pageable);

}
