package com.example.solimus.dtos.owner.meeting;

import lombok.*;

// ===== DTO FILTRE - HISTORIQUE AG D'UN COPROPRIETAIRE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingSearchFilterDTO {

    private String type;  // valeur technique MeetingType (ORDINARY/EXTRAORDINARY), null = tous types
    private Integer year; // annee de meetingDate, null = toutes annees

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;
}
