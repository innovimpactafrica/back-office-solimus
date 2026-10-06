package com.example.solimus.repositories;

import com.example.solimus.entities.EstimatedDelay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstimatedDelayRepository extends JpaRepository<EstimatedDelay, Long> {

    boolean existsByLabelIgnoreCaseAndSyndicId(String label, Long syndicId);

    // Liste d'un syndic — utilisée pour la page Paramètres (syndic connecté) et côté prestataire,
    // où le syndic est résolu à partir de l'intervention visée par le devis en cours
    List<EstimatedDelay> findBySyndicId(Long syndicId);
}
