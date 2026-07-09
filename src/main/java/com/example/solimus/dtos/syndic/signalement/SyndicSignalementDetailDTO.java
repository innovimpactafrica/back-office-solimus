package com.example.solimus.dtos.syndic.signalement;

import com.example.solimus.dtos.owner.signalement.SignalementHistoryItemDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

//DTO du détail complet d'un signalement, vue syndic
@Data
@Builder
public class SyndicSignalementDetailDTO {
    private Long id;
    private String reference;
    private String title;
    private String description;
    private String residenceName;
    private String positionLabel;
    private LocalDateTime createdAt;
    private String urgencyLevel;
    private String status;
    private List<String> photoUrls;

    // Infos du copropriétaire déclarant
    private String declaredByName;
    private String declaredByPhone;
    private String declaredByEmail;

    private String closingNote;
    private Long linkedInterventionId; // Renseigné si transformé en travaux

    private List<SignalementHistoryItemDTO> history;
}