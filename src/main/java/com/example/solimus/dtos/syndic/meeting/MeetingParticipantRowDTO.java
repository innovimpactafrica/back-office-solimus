package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.math.BigDecimal;

// ===== DTO LIGNE PARTICIPANT - ONGLET PARTICIPANTS D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipantRowDTO {

    private Long participantId;      // id du MeetingParticipant, utilisé pour l'action de signature
    private String fullName;         // nom complet du copropriétaire
    private String apartments;       // références des lots séparées par virgule (ex: "Apt 8D, Apt 4E")
    private BigDecimal tantieme;     // tantième cumulé (MeetingPresence.tantiemeSnapshot)
    private boolean hasSigned;       // a signé ou non
    private String presenceLabel;    // "Présent" / "Absent", calculé depuis hasSigned
}
