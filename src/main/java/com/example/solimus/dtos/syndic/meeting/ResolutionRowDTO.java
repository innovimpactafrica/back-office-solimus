package com.example.solimus.dtos.syndic.meeting;

import lombok.*;

// ===== DTO LIGNE - ONGLET RESOLUTIONS D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionRowDTO {

    private Long id;                    // id du MeetingAgendaItem
    private String title;
    private String description;         // peut être null

    private String resolutionStatus;    // valeur technique (ex: "EN_ATTENTE")
    private String resolutionStatusLabel; // libellé (ex: "En attente")

    private String observations;        // = resolutionText, peut être null si pas encore rempli
}
