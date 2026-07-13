package com.example.solimus.dtos.owner.meeting;

import lombok.*;
import java.time.LocalDate;

// ===== DTO CARTE "DERNIERE AG" - COPROPRIETAIRE =====
// Pas de champ vote : retire suite a la decision "pas de vote" dans ce module
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerLastMeetingDTO {

    private Long meetingId;
    private String title;
    private String type;         // valeur technique
    private String typeLabel;    // libelle affichable
    private LocalDate meetingDate;

    private boolean present;
    private String presenceLabel;
}
