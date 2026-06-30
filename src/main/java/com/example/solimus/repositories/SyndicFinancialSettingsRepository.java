package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicFinancialSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour les paramètres financiers du syndic.
 */
@Repository
public interface SyndicFinancialSettingsRepository extends JpaRepository<SyndicFinancialSettings, Long> {

    /**
     * Trouve les paramètres financiers par l'ID du syndic.
     */
    Optional<SyndicFinancialSettings> findBySyndicId(Long syndicId);
}
