package com.example.solimus.dtos.owner.signalement;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

//DTO d'une ligne de l'historique d'un signalement
@Data
@Builder
public class SignalementHistoryItemDTO {
    private String status;
    private String label; // Ex: "Pris en charge par le syndic", "Signalement envoyé"
    private String changedByName;
    private LocalDateTime date;
}