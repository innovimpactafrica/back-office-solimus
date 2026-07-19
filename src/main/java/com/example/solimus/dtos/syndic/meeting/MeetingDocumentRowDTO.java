package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// ===== DTO LIGNE DOCUMENT (utilisé sur plusieurs écrans : onglet AG, page générale) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDocumentRowDTO {

    private Long id;
    private String fileName;
    private String fileUrl;           // pour le bouton "Voir"
    private Long fileSizeKb;

    private String documentType;      // peut être null si pas encore précisé
    private String documentTypeLabel; // peut être null si pas encore précisé

    private String title;             // peut être null, front affiche fileName en secours
    private String description;       // peut être null
    private LocalDate documentDate;   // peut être null, front affiche createdAt en secours
    private String uploadedByName;    // ex: "Syndic - Abdou Diop", peut être null

    private Long meetingId;           // utile sur la page générale, pour naviguer vers la réunion
    private String meetingTitle;      // idem, pour affichage sans requête supplémentaire
    private String residenceName;     // idem

    private LocalDateTime createdAt;
}