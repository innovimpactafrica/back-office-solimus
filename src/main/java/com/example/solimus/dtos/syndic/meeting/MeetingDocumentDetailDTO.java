package com.example.solimus.dtos.syndic.meeting;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// ===== DTO DÉTAIL D'UN DOCUMENT AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDocumentDetailDTO {

    // ----- Infos document -----
    private Long id;
    private String fileName;
    private String fileUrl;
    private String documentType;
    private String documentTypeLabel;
    private String title;
    private String description;
    private LocalDate documentDate;
    private Long fileSizeKb;
    private String format;           // extension du fichier (ex: "PDF")
    private String uploadedByName;   // ex: "Syndic -Diarra"
    private LocalDateTime createdAt;

    // ----- Infos réunion parente -----
    private Long meetingId;
    private String meetingTitle;
    private String meetingType;        // valeur technique
    private String meetingTypeLabel;   // "Ordinaire" / "Extraordinaire"
    private String residenceName;
    private LocalDate meetingDate;
    private LocalTime meetingStartTime;
    private String location;
    private String organizerName;      // ex: "Syndic - Diarra"

    // ----- Présence et quorum de la réunion parente -----
    private long convoquesCount;
    private long participantsCount;
    private double quorumPercentage;

    // ----- Documents liés (autres documents de la même réunion) -----
    private List<MeetingDocumentRowDTO> linkedDocuments;

    // ----- Résolutions de la réunion parente -----
    private List<ResolutionRowDTO> resolutions;

    // ----- Historique (scopé à ce document précis) -----
    private List<MeetingHistoryRowDTO> history;
}
