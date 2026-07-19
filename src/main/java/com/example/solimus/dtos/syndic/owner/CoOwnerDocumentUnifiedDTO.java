package com.example.solimus.dtos.syndic.owner;

import lombok.*;
import java.time.LocalDateTime;

// ===== DTO LIGNE - DOCUMENT UNIFIÉ D'UN COPROPRIÉTAIRE (toutes sources confondues) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDocumentUnifiedDTO {

    // "MANUAL", "MEETING" ou "EXCEPTIONAL_CALL" — n'est stocké nulle part en base,
    // calculé à la volée dans la requête pour savoir de quelle source vient chaque ligne
    // une fois les 3 sources fusionnées ensemble
    private String sourceType;

    // Id du document DANS SA TABLE D'ORIGINE — soit CoOwnerDocument.id, soit MeetingDocument.id,
    // soit ExceptionalCallDocument.id, selon la valeur de sourceType. Ce n'est PAS un id unique
    // global : deux lignes de sources différentes peuvent avoir le même sourceId
    private Long sourceId;

    private String title;        // titre affiché (différent selon la source)
    private String fileName;
    private String fileUrl;
    private Long fileSizeKb;
    private String format;       // extension du fichier (ex: "PDF", "JPG")

    private String category;     // libellé de catégorie affiché
    private LocalDateTime createdAt;
}