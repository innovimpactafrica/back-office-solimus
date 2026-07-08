package com.example.solimus.services.owner.signalement;

import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.SignalementCardDTO;
import com.example.solimus.dtos.owner.signalement.SignalementDetailDTO;
import com.example.solimus.enums.SignalementStatus;
import org.springframework.data.domain.Page;

public interface OwnerSignalementService {

    /**
     * Crée un nouveau signalement pour le copropriétaire connecté.
     */
    void createSignalement(CreateSignalementDTO dto);

    /**
     * Liste paginée des signalements du copropriétaire connecté, avec recherche,
     * filtre par résidence et filtre par statut (tous optionnels).
     */
    Page<SignalementCardDTO> getMySignalements(String search, Long residenceId, SignalementStatus status, int page, int size);

    /**
     * Voir le détail d'un signalement du copropriétaire connecté.
     */
    SignalementDetailDTO getSignalementDetail(Long id);
}