package com.example.solimus.repositories;

import com.example.solimus.entities.InterventionStatusHistory;
import com.example.solimus.enums.InterventionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterventionStatusHistoryRepository extends JpaRepository<InterventionStatusHistory, Long> {

    /**
     * Récupérer l'historique des statuts FINISHED et FINAL_VALIDATION pour une résidence
     * Utilisé pour précharger les données de la colonne "Résolu" du Kanban
     */
    @Query("SELECT h FROM InterventionStatusHistory h " +
           "WHERE h.interventionRequest.residence.id = :residenceId " +
           "AND h.status IN (com.example.solimus.enums.InterventionStatus.FINISHED, com.example.solimus.enums.InterventionStatus.FINAL_VALIDATION) " +
           "ORDER BY h.createdAt DESC")
    List<InterventionStatusHistory> findResolvedHistoryByResidenceId(@Param("residenceId") Long residenceId);
}
