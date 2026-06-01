package com.example.solimus.dtos.meeting;

import com.example.solimus.enums.MeetingDocumentType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un document joint à une réunion.
 * Affiché : nom, taille, badge type, bouton téléchargement.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingDocumentDTO {
    private Long id;
    private String fileName;                    // "Ordre du jour complet"
    private String fileUrl;                     // URL Minio pour téléchargement
    private Long fileSizeKb;                    // "250 KB"
    private MeetingDocumentType documentType;   // badge : CONVOCATION, FINANCIER, RAPPORT
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
