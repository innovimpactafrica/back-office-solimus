package com.example.solimus.repositories;

import com.example.solimus.entities.Residence;
import com.example.solimus.enums.ResidenceHealthStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidenceRepository extends JpaRepository<Residence, Long> {

    // Récupérer toutes les résidences par ID de syndic
    List<Residence> findAllBySyndicId(Long syndicId);

    // Récupérer toutes les résidences par ID de syndic (alias)
    List<Residence> findBySyndicId(Long syndicId);

    // Compter les résidences par syndic
    long countBySyndicId(Long syndicId);

    /** Récupérer sans doublon les résidences qui ont au moins un bien vacant */
    @Query("SELECT DISTINCT r FROM Residence r JOIN r.properties p WHERE p.status = 'VACANT'")
    List<Residence> findResidencesWithVacantProperties();

    /** Compter le nombre de résidences utilisant une option de sécurité */
    @Query("SELECT COUNT(r) FROM Residence r JOIN r.securityFeatures sf WHERE sf.id = :securityFeatureId")
    long countBySecurityFeatureId(@org.springframework.data.repository.query.Param("securityFeatureId") Long securityFeatureId);

    /**
     * Recherche paginée des résidences d'un syndic avec filtres
     */
    @Query("SELECT r FROM Residence r WHERE r.syndic.id = :syndicId " +
           "AND (:search IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:city IS NULL OR r.city = :city) " +
           "ORDER BY r.name ASC")
    Page<Residence> findBySyndicIdWithFilters(
            @Param("syndicId") Long syndicId,
            @Param("search") String search,
            @Param("city") String city,
            Pageable pageable);
}
