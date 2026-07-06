package com.example.solimus.services.syndic.signalement;

import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.syndic.signalement.ResoudreSignalementDTO;
import com.example.solimus.dtos.syndic.signalement.SyndicSignalementDetailDTO;
import com.example.solimus.dtos.syndic.signalement.SyndicSignalementListDTO;
import com.example.solimus.dtos.syndic.signalement.TransformerEnTravauxDTO;

import com.example.solimus.enums.SignalementStatus;

public interface SignalementService {
    SyndicSignalementListDTO getSignalementsForSyndic(String search, SignalementStatus status, Long residenceId, int page, int size);
    SyndicSignalementDetailDTO getSignalementDetailForSyndic(Long signalementId);
    void resoudreSansTravaux(Long signalementId, ResoudreSignalementDTO dto);
    InterventionRequestDTO transformerEnTravaux(Long signalementId, TransformerEnTravauxDTO dto);
}
