package com.example.solimus.repositories;

import com.example.solimus.entities.SecurityFeature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityFeatureRepository extends JpaRepository<SecurityFeature, Long> {

    // Lister les options de sécurité d'un syndic, paginées (page Paramètres)
    Page<SecurityFeature> findBySyndicId(Long syndicId, Pageable pageable);

    // Lister les options de sécurité actives d'un syndic (sélection résidence)
    List<SecurityFeature> findBySyndicIdAndActiveTrue(Long syndicId);

    // Vérifier si un label existe déjà chez ce syndic (insensible à la casse)
    boolean existsByLabelIgnoreCaseAndSyndicId(String label, Long syndicId);
}
