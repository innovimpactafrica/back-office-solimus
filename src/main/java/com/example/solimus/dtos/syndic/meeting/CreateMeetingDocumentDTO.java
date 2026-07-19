package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.MeetingDocumentType;
import lombok.*;
import java.time.LocalDate;

// ===== DTO CRÉATION - NOUVEAU DOCUMENT DEPUIS LA PAGE DOCUMENTS GÉNÉRALE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetingDocumentDTO {

    private Long meetingId;                    // obligatoire : à quelle AG ce document appartient
    private String title;                       // optionnel
    private String description;                 // optionnel
    private LocalDate documentDate;              // optionnel
    private MeetingDocumentType documentType;    // optionnel
}