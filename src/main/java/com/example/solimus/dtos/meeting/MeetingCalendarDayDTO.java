package com.example.solimus.dtos.meeting;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Vue calendrier — regroupe les réunions d'un jour donné.
 * Le front reçoit une liste de ces objets pour colorier les jours du mois.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingCalendarDayDTO {
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate date;                  // le jour (ex: 2026-05-18)
    private List<MeetingSummaryDTO> meetings; // réunions ce jour-là
}
