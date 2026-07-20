package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.time.LocalDateTime;

// ===== DTO UNE ETAPE DE LA TIMELINE "PROGRESSION DE LA DEMANDE" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalProgressStepDTO {

    private String label;        // "Demande créée", "En attente de validation", "Validée", "Refusée"
    private String state;        // "DONE", "CURRENT", "PENDING", "REJECTED"
    private LocalDateTime date;  // peut etre null si l'etape n'est pas encore atteinte
    private String durationLabel; // ex: "En cours depuis 4h", null si l'étape n'est pas en cours
}
