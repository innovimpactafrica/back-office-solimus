package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.time.LocalDateTime;

//DTO d'une ligne d'historique d'un appel exceptionnel (onglet Historique)
@Data
public class ExceptionalCallHistoryDTO {
    private String actorName; // Auteur de l'action
    private String statusBadge; // Statut au moment de l'action
    private String message; // Description de l'action
    private LocalDateTime createdAt; // Date de l'action
}
