package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderRatingRepository extends JpaRepository<ProviderRating, Long> {

    /**
     * Calcule la note moyenne globale d'un prestataire basé sur toutes ses évaluations.
     * Retourne null s'il n'a encore reçu aucune note.
     */
    @Query("SELECT AVG(r.rating) FROM ProviderRating r WHERE r.provider.id = :providerId")
    Double calculerNoteMoyenne(@Param("providerId") Long providerId);

    /**
     * Compte le nombre total d'évaluations reçues par un prestataire.
     */
    long countByProviderId(Long providerId);

    /**
     * Calcule le taux de satisfaction d'un prestataire (pourcentage de notes >= 3).
     * Retourne null s'il n'a encore reçu aucune note.
     */
    @Query("SELECT ROUND((COUNT(r) * 100.0 / NULLIF((SELECT COUNT(r2) FROM ProviderRating r2 WHERE r2.provider.id = :providerId), 0)), 0) FROM ProviderRating r WHERE r.provider.id = :providerId AND r.rating >= 3")
    Integer calculerTauxSatisfaction(@Param("providerId") Long providerId);

    /**
     * Calcule le temps moyen d'intervention en minutes.
     * Retourne null s'il n'a encore aucune intervention terminée.
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(MINUTE, i.startedAt, i.finishedAt)) " +
           "FROM InterventionRequest i " +
           "WHERE i.selectedProvider.id = :providerId " +
           "AND i.startedAt IS NOT NULL AND i.finishedAt IS NOT NULL")
    Double calculerTempsIntervention(@Param("providerId") Long providerId);
}
