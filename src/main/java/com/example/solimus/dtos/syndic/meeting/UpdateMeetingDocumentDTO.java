package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.MeetingDocumentType;
import com.example.solimus.utils.FlexibleLocalDateDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

    // Accepte une date ISO (yyyy-MM-dd) OU un ISO datetime complet dans le body JSON
    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    @JsonAlias({"date"})
    private LocalDate documentDate;

    private MeetingDocumentType documentType;
}