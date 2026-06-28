package com.example.solimus.repositories;

import com.example.solimus.entities.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Trouver un avis par intervention
     */
    Optional<Review> findByInterventionRequestId(Long interventionRequestId);

    /**
     * Vérifier si un avis existe pour une intervention
     */
    boolean existsByInterventionRequestId(Long interventionRequestId);

    /**
     * Trouver tous les avis pour un prestataire
     */
    List<Review> findByProviderId(Long providerId);

    /**
     * Calcule la note moyenne globale d'un prestataire basé sur toutes ses évaluations.
     * Retourne null s'il n'a encore reçu aucune note.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.provider.id = :providerId")
    Double calculerNoteMoyenne(@Param("providerId") Long providerId);

    /**
     * Compte le nombre total d'évaluations reçues par un prestataire.
     */
    long countByProviderId(Long providerId);

    /**
     * Calcule le taux de satisfaction d'un prestataire (pourcentage de notes >= 3).
     * Retourne null s'il n'a encore reçu aucune note.
     */
    @Query(value = "SELECT ROUND((COUNT(r) * 100.0 / NULLIF((SELECT COUNT(r2) FROM reviews r2 WHERE r2.provider_id = :providerId), 0)), 0) " +
           "FROM reviews r WHERE r.provider_id = :providerId AND r.rating >= 3", nativeQuery = true)
    Integer calculerTauxSatisfaction(@Param("providerId") Long providerId);

    /**
     * Calcule le temps moyen d'intervention en minutes.
     * Retourne null s'il n'a encore aucune intervention terminée.
     */
    @Query(value = "SELECT AVG(TIMESTAMPDIFF(MINUTE, i.started_at, i.finished_at)) " +
           "FROM intervention_requests i " +
           "WHERE i.selected_provider_id = :providerId " +
           "AND i.started_at IS NOT NULL AND i.finished_at IS NOT NULL", nativeQuery = true)
    Double calculerTempsIntervention(@Param("providerId") Long providerId);
}
