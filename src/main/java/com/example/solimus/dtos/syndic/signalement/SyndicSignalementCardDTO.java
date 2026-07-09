package com.example.solimus.dtos.syndic.signalement;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

//DTO d'une carte de signalement dans la liste syndic
@Data
@Builder
public class SyndicSignalementCardDTO {
    private Long id;
    private String title;
    private String positionLabel; // "Appartement B12"
    private String residenceName;
    private String declaredByName; // Nom du copropriétaire
    private LocalDateTime createdAt;
    private String urgencyLevel;
    private String status;
}