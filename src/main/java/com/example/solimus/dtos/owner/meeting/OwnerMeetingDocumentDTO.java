package com.example.solimus.dtos.owner.meeting;

import lombok.*;

// ===== DTO DOCUMENT - DETAIL REUNION (APP MOBILE COPROPRIETAIRE) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingDocumentDTO {

    private Long id;
    private String fileName;
    private String fileUrl;           // pour le téléchargement
    private Long fileSizeKb;
    private String documentTypeLabel; // "Convocation", "Financier", "Rapport"...
}