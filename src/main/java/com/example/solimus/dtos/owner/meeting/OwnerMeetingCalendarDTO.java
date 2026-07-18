package com.example.solimus.dtos.owner.meeting;

import lombok.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// ===== DTO CALENDRIER - REUNIONS D'UN MOIS, GROUPEES PAR JOUR =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingCalendarDTO {

    // Clé = la date (ex: 2026-05-18), valeur = les reunions de ce jour précis
    private Map<LocalDate, List<OwnerMeetingCardDTO>> meetingsByDate;
}