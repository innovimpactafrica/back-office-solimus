package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerInterventionSummaryDTO {

    private Long id;
    private String title;

    // Nom de la résidence
    private String residenceName;

    // CAS APPARTEMENT → type de bien (ex: "Appartement", "Studio")
    // CAS PARTIE_COMMUNE → null
    private String typeBien;

    // CAS APPARTEMENT → null
    // CAS PARTIE_COMMUNE → nom de la partie commune (ex: "Ascenseur Bloc A")
    private String commonFacilityName;

    // Nom de la spécialité (ex: "Plomberie", "Électricité")
    // Utilisé aussi pour l'icône → specialty.icon
    private String specialtyName;

    // Icône de la spécialité → ex: "plumbing", "electrical"
    // Le front mappe ce nom vers une icône locale
    private String specialtyIcon;

    // Statut affiché en clair → status.getLabel()
    // Ex: "En cours", "Terminé"
    private String statusLabel;

    // Valeur brute du statut pour la logique front
    private InterventionStatus status;

    // Niveau d'urgence affiché → urgencyLevel.getLabel()
    // Ex: "Urgent", "Critique"
    private String urgencyLabel;

    // Valeur brute urgence
    private UrgencyLevel urgencyLevel;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
