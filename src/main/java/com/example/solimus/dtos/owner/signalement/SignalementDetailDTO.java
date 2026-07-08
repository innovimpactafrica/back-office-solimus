package com.example.solimus.dtos.owner.signalement;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

//DTO du détail complet d'un signalement
@Data
@Builder
public class SignalementDetailDTO {
    private Long id;
    private String reference;
    private String title;
    private String residenceName;
    private String positionLabel;
    private LocalDateTime createdAt;
    private String urgencyLevel;
    private String status;
    private String description;
    private List<String> photoUrls;
    private String declaredByName; // Nom du copropriétaire (utile côté syndic)
    private String closingNote;
    private List<SignalementHistoryItemDTO> history;
}