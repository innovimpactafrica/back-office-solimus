package com.example.solimus.repositories;

import com.example.solimus.entities.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PropertyTypeRepository extends JpaRepository<PropertyType, Long> {

    boolean existsByNameIgnoreCaseAndSyndicId(String name, Long syndicId);

    // Catalogue propre à chaque syndic — jamais celui d'un autre
    Page<PropertyType> findBySyndicId(Long syndicId, Pageable pageable);

    // Pour vérifier qu'un syndic modifie/supprime bien un type qui lui appartient
    Optional<PropertyType> findByIdAndSyndicId(Long id, Long syndicId);
}
