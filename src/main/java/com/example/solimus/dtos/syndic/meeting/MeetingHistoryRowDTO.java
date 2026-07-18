package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.time.LocalDateTime;

// ===== DTO LIGNE - ONGLET HISTORIQUE D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingHistoryRowDTO {

    private String message;       // titre court
    private String actorName;     // "Syndic principal" ou nom complet de l'acteur
    private LocalDateTime createdAt;
}