package com.example.solimus.dtos.owner.meeting;

import lombok.*;
import java.time.LocalDate;

// ===== DTO LIGNE HISTORIQUE - UNE AG DANS LE TABLEAU DU COPROPRIETAIRE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingHistoryRowDTO {

    private Long meetingId;
    private String title;
    private LocalDate meetingDate;

    private double quorumPercentage;  // % tantieme present, calcule sur toute la reunion (pas juste ce coproprietaire)

    private boolean present;          // hasSigned de CE coproprietaire pour cette reunion
    private String presenceLabel;     // "Présent" / "Absent"
}
