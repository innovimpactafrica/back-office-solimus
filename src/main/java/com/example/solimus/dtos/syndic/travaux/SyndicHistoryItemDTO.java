package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

//DTO d'une ligne de l'historique complet (onglet 4, syndic)
@Data
@Builder
public class SyndicHistoryItemDTO {
    private String actorName;
    private String actorRole; // "Copropriétaire", "Gestionnaire", "Prestataire"
    private String label;
    private LocalDateTime date;
}
