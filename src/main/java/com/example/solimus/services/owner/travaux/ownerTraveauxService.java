package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.enums.InterventionStatus;

import java.util.List;

public interface ownerTraveauxService {

    /**
     * Lister toutes les résidences où le copropriétaire connecté possède un bien.
     */
    List<ResidenceDTO> getMyResidences();

    /**
     * Lister les parties communes d'une résidence où il a au moins un bien.
     */
    List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId);

    /**
     * Lister mes biens dans une résidence donnée.
     */
    List<PropertyDTO> getMyPropertiesByResidence(Long residenceId);

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================
    /**
     * Créer une demande d'intervention initiée par le copropriétaire.
     * - APPARTEMENT : managementMode = OWNER (géré par le copropriétaire)
     * - PARTIE_COMMUNE : managementMode = SYNDIC (géré par le syndic)
     */
    void createIntervention(CreateOwnerInterventionRequestDTO dto);

    // =========================================================================
    // LISTER MES DEMANDES DE TRAVAUX
    // =========================================================================
    /**
     * Lister, filtrer et paginer les demandes de travaux du copropriétaire connecté.
     * @param search      recherche par titre (optionnel)
     * @param status      filtre par statut (optionnel)
     * @param residenceId filtre par résidence (optionnel)
     * @param page        numéro de page (défaut 0)
     * @param size        taille de page (défaut 10)
     */
    OwnerInterventionDTO getMyInterventions(String search, InterventionStatus status, Long residenceId, int page, int size);

    /**
     * Récupérer les détails d'une intervention spécifique du copropriétaire.
     */
    OwnerInterventionDetailDTO getInterventionDetail(Long interventionId);

    /**
     * Créer un avis pour une intervention terminée.
     */
    void createReview(Long interventionId, CreateReviewDTO dto);

}
