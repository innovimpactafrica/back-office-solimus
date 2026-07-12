package com.example.solimus.dtos.syndic.charge;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

//DTO d'une ligne d'historique pour un budget
@Data
@Builder
public class BudgetHistoryItemDTO {
    private String actorName; // Nom de la personne à l'origine de l'événement
    private String message; // Description de l'événement
    private LocalDateTime date; // Date de l'événement
}
