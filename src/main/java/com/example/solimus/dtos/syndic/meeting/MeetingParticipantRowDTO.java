package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.AttendanceType;
import lombok.*;
import java.math.BigDecimal;

// ===== DTO LIGNE PARTICIPANT - ONGLET PARTICIPANTS D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipantRowDTO {

    private Long participantId;      // id du MeetingParticipant
    private String fullName;         // nom complet du copropriétaire
    private String apartments;       // références des lots séparées par virgule (ex: "Apt 8D, Apt 4E")
    private BigDecimal tantieme;     // tantième cumulé (MeetingPresence.tantiemeSnapshot)
    private AttendanceType attendanceType; // PRESENT / PROXY / ABSENT — déclaré par le copropriétaire lui-même
    private String presenceLabel;    // "Présent" / "Procuration" / "Absent", calculé depuis attendanceType
    private String mandataireName;   // nom du mandataire, rempli uniquement si attendanceType = PROXY
}
