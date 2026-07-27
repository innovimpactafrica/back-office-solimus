package com.example.solimus.repositories;

import com.example.solimus.entities.FacilityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface FacilityTypeRepository extends JpaRepository<FacilityType, Long> {

        boolean existsByNameIgnoreCaseAndSyndicId(String name, Long syndicId);

        // Catalogue propre à chaque syndic — jamais celui d'un autre
        Page<FacilityType> findBySyndicId(Long syndicId, Pageable pageable);

        // Pour vérifier qu'un syndic modifie/supprime bien un type qui lui appartient
        Optional<FacilityType> findByIdAndSyndicId(Long id, Long syndicId);

        // récupère les types d'équipements actifs du syndic connecté — pour afficher les blocs à l'étape 3
        List<FacilityType> findBySyndicIdAndIsActiveTrue(Long syndicId);

        // récupère les types d'équipements actifs du syndic connecté avec pagination
        Page<FacilityType> findBySyndicIdAndIsActiveTrue(Long syndicId, Pageable pageable);
}
