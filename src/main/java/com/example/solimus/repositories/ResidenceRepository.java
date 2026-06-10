package com.example.solimus.repositories;

import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidenceRepository extends JpaRepository<Residence, Long> {

    // Récupérer toutes les résidences gérées par un syndic spécifique
    List<Residence> findAllBySyndic(User syndic);

    // Récupérer toutes les résidences par ID de syndic
    List<Residence> findAllBySyndicId(Long syndicId);
}
