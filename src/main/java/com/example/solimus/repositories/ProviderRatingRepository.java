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
}
