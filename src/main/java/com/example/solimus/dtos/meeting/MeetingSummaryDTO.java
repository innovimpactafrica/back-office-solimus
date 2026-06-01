package com.example.solimus.dtos.meeting;

import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime meetingDate;
    private String location;
    private int participantCount;   // "45 participants"
    private int documentCount;      // "5 document(s)"
    private Long residenceId;
}
