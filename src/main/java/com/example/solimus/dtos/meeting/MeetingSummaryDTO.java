package com.example.solimus.dtos.meeting;

import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO léger pour une carte dans la liste des réunions.
 * Affiché : titre, type, statut, date, lieu, nb participants, nb documents.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingSummaryDTO {
    private Long id;
    private String title;
    private MeetingType type;       // badge type
    private MeetingStatus status;   // badge statut

    // Séparés pour que le front affiche facilement sur 2 lignes
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate meetingDate;      // 📅 "25/05/2026"

    private String meetingStartTime;   // 🕕 "18:00"
    private String meetingEndTime;     // 🕕 "20:00"

    private String location;
    private int participantCount;
    private int documentCount;
    private Long residenceId;
}
