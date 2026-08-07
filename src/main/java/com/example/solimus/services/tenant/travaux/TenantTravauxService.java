package com.example.solimus.services.tenant.travaux;

import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.tenant.TenantPropertyInfoDTO;
import com.example.solimus.dtos.tenant.travaux.CreateTenantInterventionRequestDTO;
import com.example.solimus.enums.InterventionStatus;

import java.util.List;

public interface TenantTravauxService {

    /**
     * Retourne la référence du bien du locataire connecté + le nom de la résidence.
     */
    TenantPropertyInfoDTO getMyProperty();

    /**
     * Lister les parties communes de la résidence du locataire connecté.
     */
    List<CommonFacilityDTO> getCommonFacilities();

    /**
     * Créer une demande de travaux initiée par le locataire connecté.
     * Toujours gérée manuellement par le syndic (pas de circuit prestataire).
     */
    void createIntervention(CreateTenantInterventionRequestDTO dto);

    /**
     * Lister, filtrer et paginer les demandes de travaux du locataire connecté.
     */
    OwnerInterventionDTO getMyInterventions(String search, InterventionStatus status, int page, int size);

    /**
     * Récupérer les détails d'une demande de travaux du locataire connecté.
     */
    OwnerInterventionDetailDTO getInterventionDetail(Long interventionId);
}
