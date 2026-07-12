package com.example.solimus.dtos.syndic.charge;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

//DTO d'un document rattaché à un appel exceptionnel (onglet Documents)
@Data
@Builder
public class ExceptionalCallDocumentDTO {
    private Long id; // Identifiant du document
    private String fileName; // Nom du fichier
    private String fileUrl; // URL de téléchargement
    private String fileExtension; // Extension (pdf, jpg, png, zip)
    private Double fileSizeMb; // Taille du fichier en mégaoctets
    private LocalDateTime createdAt; // Date d'ajout
}
