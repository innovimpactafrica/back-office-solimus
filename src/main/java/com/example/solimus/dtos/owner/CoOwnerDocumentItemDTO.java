package com.example.solimus.dtos.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDocumentItemDTO {

    // Titre du document
    private String title;

    // Catégorie affichée (Titre de propriété, Contrats, Pièces d'identité, PV d'assemblée, Reçus de paiement)
    private String category;

    // Date de création/upload
    private LocalDateTime date;

    // Taille du fichier en KB
    private Long fileSizeKb;

    // Type de fichier (PDF, JPG, etc.)
    private String fileType;

    // URL du fichier
    private String fileUrl;
}
