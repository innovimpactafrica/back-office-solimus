package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.travaux.CreateInterventionRequestDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;

import java.util.List;

public interface SyndicTravauxService {

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================
    void createInterventionRequest(CreateInterventionRequestDTO dto);

    // =========================================================================
    // ENVOI AUX PRESTATAIRES
    // =========================================================================
    /**
     * Envoie une demande de partie commune créée par un owner aux prestataires proches.
     * Le syndic doit valider et diffuser la demande aux prestataires.
     */
    void sendToProviders(Long interventionId);

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================
    /**
     * Lister toutes les résidences créées par le syndic connecté.
     */
    List<SyndicResidenceDTO> getMesResidences();

    // =========================================================================
    // LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================
    /**
     * Lister tous les lots d'une résidence spécifique.
     * Vérifie que la résidence appartient au syndic connecté.
     */
    List<PropertyDTO> getPropertiesByResidence(Long residenceId);

    // =========================================================================
    // LISTER LES BIENS COMMUNS D'UNE RÉSIDENCE
    // =========================================================================
    /**
     * Lister les biens communs d'une résidence.
     * Vérifie que la résidence appartient au syndic connecté.
     */
    List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId);

    // =========================================================================
    // LISTER LES SPÉCIALITÉS
    // =========================================================================
    /**
     * Lister toutes les spécialités disponibles pour la création d'intervention.
     */
    List<SpecialtyDTO> getAllSpecialties();

    // =========================================================================
    // CRÉATION D'AVIS
    // =========================================================================
    /**
     * Créer un avis pour une intervention terminée.
     */
    void createReview(Long interventionId, CreateReviewDTO dto);

}
