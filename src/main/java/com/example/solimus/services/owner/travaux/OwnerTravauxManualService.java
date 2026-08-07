package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.enums.InterventionStatus;

import java.util.List;

/**
 * Flux manuel de gestion des travaux côté copropriétaire — remplace le matching prestataire par
 * une notification systématique du syndic, tant que le circuit prestataire digitalisé reste
 * désactivé. Réutilise les mêmes DTOs que {@link ownerTravauxService}.
 */
public interface OwnerTravauxManualService {

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

    /**
     * Créer une demande de travaux initiée par le copropriétaire.
     * Toujours gérée manuellement par le syndic (pas de circuit prestataire).
     */
    void createIntervention(CreateOwnerInterventionRequestDTO dto);

    /**
     * Lister, filtrer et paginer les demandes de travaux du copropriétaire connecté.
     */
    OwnerInterventionDTO getMyInterventions(String search, InterventionStatus status, Long residenceId, int page, int size);

    /**
     * Récupérer les détails d'une intervention spécifique du copropriétaire.
     */
    OwnerInterventionDetailDTO getInterventionDetail(Long interventionId);
}
