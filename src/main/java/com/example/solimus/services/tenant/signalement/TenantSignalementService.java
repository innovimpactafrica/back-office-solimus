package com.example.solimus.services.tenant.signalement;

import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.SignalementCardDTO;
import com.example.solimus.dtos.owner.signalement.SignalementDetailDTO;
import com.example.solimus.dtos.tenant.TenantPropertyInfoDTO;
import com.example.solimus.enums.SignalementStatus;
import org.springframework.data.domain.Page;

public interface TenantSignalementService {

    /**
     * Crée un nouveau signalement pour le locataire connecté, sur son bien loué.
     */
    void createSignalement(CreateSignalementDTO dto);

    /**
     * Liste paginée des signalements du locataire connecté, avec recherche et filtre par statut.
     * Pas de filtre résidence : un locataire n'a qu'un seul bien.
     */
    Page<SignalementCardDTO> getMySignalements(String search, SignalementStatus status, int page, int size);

    /**
     * Voir le détail d'un signalement du locataire connecté.
     */
    SignalementDetailDTO getSignalementDetail(Long id);

    /**
     * Retourne la référence du bien du locataire connecté + le nom de la résidence.
     */
    TenantPropertyInfoDTO getMyProperty();
}
