package com.example.solimus.repositories;

import com.example.solimus.entities.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {
    
    // Vérifier si un bien avec cette référence existe déjà dans une résidence donnée
    boolean existsByReferenceAndResidenceId(String reference, Long residenceId);
}
