package com.example.solimus.repositories;

import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidenceRepository extends JpaRepository<Residence, Long> {

    // Récupérer toutes les résidences gérées par un syndic spécifique
    List<Residence> findAllBySyndic(User syndic);

    // Récupérer toutes les résidences par ID de syndic
    List<Residence> findAllBySyndicId(Long syndicId);

    /** Récupérer sans doublon les résidences qui ont au moins un bien vacant */
    @Query("SELECT DISTINCT r FROM Residence r JOIN r.properties p WHERE p.status = 'VACANT'")
    List<Residence> findResidencesWithVacantProperties();
}
