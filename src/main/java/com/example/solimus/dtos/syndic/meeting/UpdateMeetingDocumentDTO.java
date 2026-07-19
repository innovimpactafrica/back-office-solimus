package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.MeetingDocumentType;
import lombok.*;
import java.time.LocalDate;

// ===== DTO MISE À JOUR - MÉTADONNÉES D'UN DOCUMENT EXISTANT =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeetingDocumentDTO {

    private String title;
    private String description;
    private LocalDate documentDate;
    private MeetingDocumentType documentType;
}