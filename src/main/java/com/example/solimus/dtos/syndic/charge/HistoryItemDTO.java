package com.example.solimus.dtos.syndic.charge;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

//DTO générique d'une ligne d'historique, réutilisable pour Budget, ChargeCall, ExceptionalCall...
@Data
@Builder
public class HistoryItemDTO {
    private String actorName; // Nom de la personne à l'origine de l'événement
    private String message; // Description de l'événement
    private LocalDateTime date; // Date de l'événement
}
