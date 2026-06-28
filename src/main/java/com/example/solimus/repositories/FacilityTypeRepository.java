package com.example.solimus.repositories;

import com.example.solimus.entities.FacilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface FacilityTypeRepository extends JpaRepository<FacilityType, Long> {

        boolean existsByNameIgnoreCase(String name);

        // récupère tous les types d'équipements actifs — pour afficher les blocs à l'étape 3
        List<FacilityType> findByIsActiveTrue();
}
