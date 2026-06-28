package com.example.solimus.dtos.owner.travaux;

import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Carte d'une demande de travaux affichée dans la liste côté copropriétaire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerInterventionSummaryDTO {

    private Long id;

    // Titre de la demande (ex: "Fuite d'eau - Salle de bain")
    private String title;

    // Nom de la résidence (ex: "Résidence Les Jardins")
    private String residenceName;

    // CAS APPARTEMENT → référence du bien (ex: "A12", "Villa B4")
    // CAS PARTIE_COMMUNE → null
    private String propertyReference;

    // CAS PARTIE_COMMUNE → nom de la partie commune (ex: "Ascenseur Bloc A")
    // CAS APPARTEMENT → null
    private String commonFacilityName;

    // Nom de la spécialité (ex: "Plomberie", "Électricité")
    private String specialtyName;

    // Icône de la spécialité → le front mappe ce nom vers une icône locale
    private String specialtyIcon;

    // Statut affiché en clair → status.getLabel() (ex: "Terminé", "Pris en charge")
    private String statusLabel;

    // Valeur brute du statut pour la logique front
    private InterventionStatus status;

    // Niveau d'urgence affiché → urgencyLevel.getLabel() (ex: "Urgent")
    private String urgencyLabel;

    // Valeur brute de l'urgence
    private UrgencyLevel urgencyLevel;

    // Date et heure de signalement (ex: "08/05/2026 14:30")
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
