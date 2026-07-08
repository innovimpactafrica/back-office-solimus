package com.example.solimus.dtos.owner.signalement;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

//DTO d'une carte de signalement dans la liste "Mes signalements"
@Data
@Builder
public class SignalementCardDTO {
    private Long id;
    private String title;
    private String positionLabel; // Ex: "Appartement B12"
    private LocalDateTime createdAt;
    private String urgencyLevel;
    private String status;
}