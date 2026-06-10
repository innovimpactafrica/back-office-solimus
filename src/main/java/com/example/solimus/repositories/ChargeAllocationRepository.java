package com.example.solimus.repositories;

import com.example.solimus.entities.Charge;
import com.example.solimus.entities.ChargeAllocation;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité ChargeAllocation.
 * 
 * Ce repository fournit les méthodes d'accès aux données pour les allocations de charge.
 * Il étend JpaRepository pour bénéficier des méthodes CRUD standard
 * et définit des méthodes de recherche personnalisées.
 * 
 * Méthodes principales :
 * - findByCharge : trouver toutes les allocations d'une charge
 * - findByOwner : trouver toutes les allocations d'un copropriétaire
 * - findByProperty : trouver toutes les allocations d'un bien
 * - findByChargeAndOwner : trouver l'allocation d'une charge pour un copropriétaire spécifique
 * - findByStatus : trouver les allocations selon leur statut de paiement
 */
@Repository
public interface ChargeAllocationRepository extends JpaRepository<ChargeAllocation, Long> {

    /**
     * Trouve toutes les allocations d'une charge donnée.
     * 
     * @param charge la charge parente
     * @return la liste des allocations de cette charge
     */
    List<ChargeAllocation> findByCharge(Charge charge);

    /**
     * Trouve toutes les allocations d'un copropriétaire donné.
     *
     * @param owner le copropriétaire concerné
     * @return la liste des allocations de ce copropriétaire, triées par date de création décroissante
     */
    @Query("SELECT a FROM ChargeAllocation a JOIN FETCH a.charge WHERE a.owner = :owner ORDER BY a.createdAt DESC")
    List<ChargeAllocation> findByOwnerOrderByCreatedAtDesc(@Param("owner") User owner);

    /**
     * Trouve toutes les allocations d'un copropriétaire triées par date d'échéance de la charge.
     *
     * @param ownerId l'ID du copropriétaire
     * @return la liste des allocations triées par date d'échéance croissante
     */
    List<ChargeAllocation> findAllByOwnerIdOrderByChargeDueDateAsc(Long ownerId);

    /**
     * Trouve les 3 premières allocations EN_ATTENTE d'un propriétaire pour un bien donné.
     */
    List<ChargeAllocation> findTop3ByOwnerIdAndPropertyIdAndStatusOrderByChargeDueDateAsc(
            Long ownerId, Long propertyId, ChargeStatus status);

    /**
     * Compte le nombre de documents de charges pour un propriétaire.
     */
    @Query("SELECT SUM(SIZE(c.documentUrls)) FROM Charge c JOIN c.allocations a WHERE a.owner.id = :ownerId")
    int countDocumentsByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * Calcule la somme des montants d'allocations pour une résidence et un statut donné.
     */
    @Query("SELECT SUM(a.amount) FROM ChargeAllocation a JOIN a.charge c WHERE c.residence.id = :residenceId AND a.status = :status")
    java.math.BigDecimal sumByResidenceIdAndStatus(@Param("residenceId") Long residenceId, @Param("status") ChargeStatus status);

    /**
     * Trouve toutes les allocations d'un bien donné.
     * 
     * @param property le bien concerné
     * @return la liste des allocations de ce bien
     */
    List<ChargeAllocation> findByProperty(Property property);

    /**
     * Trouve l'allocation d'une charge pour un copropriétaire spécifique.
     * 
     * @param charge la charge concernée
     * @param owner le copropriétaire concerné
     * @return un Optional contenant l'allocation si elle existe, vide sinon
     */
    Optional<ChargeAllocation> findByChargeAndOwner(Charge charge, User owner);

    /**
     * Trouve toutes les allocations avec un statut donné.
     * 
     * @param status le statut de paiement (EN_ATTENTE, PAYEE, EN_RETARD)
     * @return la liste des allocations avec ce statut
     */
    List<ChargeAllocation> findByStatus(ChargeStatus status);

    /**
     * Trouve toutes les allocations en attente pour une charge donnée.
     * 
     * @param charge la charge concernée
     * @return la liste des allocations non payées de cette charge
     */
    List<ChargeAllocation> findByChargeAndStatus(Charge charge, ChargeStatus status);

    /**
     * Trouve toutes les allocations en retard de paiement.
     * 
     * @return la liste des allocations avec le statut EN_RETARD
     */
    List<ChargeAllocation> findByStatusOrderByCreatedAtDesc(ChargeStatus status);

    /**
     * Compte le nombre d'allocations payées pour une charge donnée.
     * 
     * @param charge la charge concernée
     * @return le nombre d'allocations avec le statut PAYEE
     */
    long countByChargeAndStatus(Charge charge, ChargeStatus status);

    /**
     * Vérifie si une allocation existe déjà pour une charge et un bien donnés.
     *
     * @param charge la charge concernée
     * @param propertyId l'ID du bien
     * @return true si une allocation existe, false sinon
     */
    boolean existsByChargeAndPropertyId(Charge charge, Long propertyId);

    /**
     * Recherche paginée des allocations d'un copropriétaire avec filtres.
     *
     * @param ownerId l'ID du copropriétaire
     * @param status le statut optionnel (null pour tous)
     * @param search le terme de recherche dans le titre de la charge (null pour tous)
     * @param pageable les paramètres de pagination
     * @return la page des allocations correspondantes
     */
    @Query("SELECT a FROM ChargeAllocation a WHERE a.owner.id = :ownerId " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:search IS NULL OR LOWER(a.charge.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY a.charge.dueDate ASC")
    Page<ChargeAllocation> findByOwnerIdWithFilters(
        @Param("ownerId") Long ownerId,
        @Param("status") ChargeStatus status,
        @Param("search") String search,
        Pageable pageable);

    /**
     * Supprime toutes les allocations d'une charge donnée.
     *
     * @param charge la charge concernée
     */
    void deleteByCharge(Charge charge);

    /**
     * Trouve toutes les allocations en attente dont la date d'échéance est passée.
     *
     * @param status le statut (EN_ATTENTE)
     * @param date la date limite
     * @return la liste des allocations en retard
     */
    List<ChargeAllocation> findAllByStatusAndChargeDueDateBefore(ChargeStatus status, java.time.LocalDate date);

    /**
     * Compte le nombre total d'allocations pour une résidence.
     *
     * @param residenceId l'ID de la résidence
     * @return le nombre d'allocations
     */
    long countByChargeResidenceId(Long residenceId);

    /**
     * Compte le nombre d'allocations avec un statut donné pour une résidence.
     *
     * @param residenceId l'ID de la résidence
     * @param status le statut des allocations
     * @return le nombre d'allocations avec ce statut
     */
    long countByChargeResidenceIdAndStatus(Long residenceId, ChargeStatus status);
}
