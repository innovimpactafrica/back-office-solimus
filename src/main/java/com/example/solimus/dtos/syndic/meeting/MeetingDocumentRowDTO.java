package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.time.LocalDateTime;

// ===== DTO LIGNE - ONGLET DOCUMENTS D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDocumentRowDTO {

    private Long id;
    private String fileName;
    private String fileUrl;           // pour le bouton "Voir" (oeil)
    private Long fileSizeKb;
    private String documentType;      // valeur technique (ex: "OTHER")
    private String documentTypeLabel; // libelle (ex: "Autre")
    private LocalDateTime createdAt;
}