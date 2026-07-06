package com.example.solimus.dtos.meeting;

import com.example.solimus.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipantRowDTO {

    // Nom complet du copropriétaire
    private String coOwnerName;

    // Liste des références de lots dans cette résidence (ex: ["Apt 8D", "Apt 4E"])
    private List<String> apartmentReferences;

    // Tantième snapshot (figé au moment de la prise de présence)
    private BigDecimal tantieme;

    // Statut de présence
    private AttendanceStatus attendanceStatus;

    // Nom du représentant (si procuration)
    private String representedByName;

    // Signature électronique
    private Boolean hasSigned;
}
