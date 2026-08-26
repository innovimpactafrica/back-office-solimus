package com.example.solimus.repositories;

import com.example.solimus.entities.ExceptionalCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExceptionalCallRepository extends JpaRepository<ExceptionalCall, Long> {

    // Récupère les appels exceptionnels d'un syndic, paginés, avec filtres résidence et année
    // tous les deux optionnels (année = YEAR(createdAt), pas de champ "year" dédié sur cette entité)
    @Query("SELECT ec FROM ExceptionalCall ec WHERE ec.syndic.id = :syndicId " +
           "AND (:residenceId IS NULL OR ec.residence.id = :residenceId) " +
           "AND (:year IS NULL OR YEAR(ec.createdAt) = :year) " +
           "ORDER BY ec.createdAt DESC")
    Page<ExceptionalCall> findBySyndicIdWithFilters(@Param("syndicId") Long syndicId,
                                                     @Param("residenceId") Long residenceId,
                                                     @Param("year") Integer year,
                                                     Pageable pageable);
}
