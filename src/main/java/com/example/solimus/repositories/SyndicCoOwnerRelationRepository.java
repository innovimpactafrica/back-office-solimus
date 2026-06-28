package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicOwnerRelation;
import com.example.solimus.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyndicCoOwnerRelationRepository extends JpaRepository<SyndicOwnerRelation, Long> {

    // Vérifier si une relation existe déjà entre un syndic et un copropriétaire
    Optional<SyndicOwnerRelation> findBySyndicIdAndCoOwnerId(Long syndicId, Long coOwnerId);

    // Récupérer toutes les relations d'un syndic (paginé)
    Page<SyndicOwnerRelation> findAllBySyndicId(Long syndicId, Pageable pageable);

    // Rechercher les copropriétaires liés à un syndic avec filtre de recherche
    @Query("SELECT r.coOwner FROM SyndicOwnerRelation r " +
           "WHERE r.syndic.id = :syndicId " +
           "AND (:search IS NULL OR LOWER(r.coOwner.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(r.coOwner.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(r.coOwner.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> findCoOwnersBySyndicIdWithSearch(@Param("syndicId") Long syndicId, @Param("search") String search);

    // Rechercher les copropriétaires liés à un syndic avec filtre de recherche ET ayant au moins un bien (pour l'affectation de lots)
    @Query("SELECT r.coOwner FROM SyndicOwnerRelation r " +
           "WHERE r.syndic.id = :syndicId " +
           "AND EXISTS (SELECT 1 FROM Property p WHERE p.owner = r.coOwner) " +
           "AND (:search IS NULL OR LOWER(r.coOwner.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(r.coOwner.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(r.coOwner.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<User> findCoOwnersWithPropertiesBySyndicIdWithSearch(@Param("syndicId") Long syndicId, @Param("search") String search);

    // Récupérer les relations d'un syndic pour les copropriétaires qui ont au moins un bien (paginé)
    @Query("SELECT r FROM SyndicOwnerRelation r " +
           "WHERE r.syndic.id = :syndicId " +
           "AND EXISTS (SELECT 1 FROM Property p WHERE p.owner = r.coOwner)")
    Page<SyndicOwnerRelation> findCoOwnersWithPropertiesBySyndicId(@Param("syndicId") Long syndicId, Pageable pageable);
}
