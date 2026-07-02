package com.example.solimus.repositories;

import com.example.solimus.entities.SecurityFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityFeatureRepository extends JpaRepository<SecurityFeature, Long> {

    // Lister toutes les options de sécurité actives
    List<SecurityFeature> findByActiveTrue();

    // Vérifier si un label existe déjà
    boolean existsByLabel(String label);

    // Vérifier si un label existe déjà (insensible à la casse)
    boolean existsByLabelIgnoreCase(String label);
}
