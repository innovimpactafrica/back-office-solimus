package com.example.solimus.dtos.owner.travaux;

import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// DTO contenant les détails complets d'une intervention pour le copropriétaire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerInterventionDetailDTO {

    private Long id;
    private String title;
    private String description;

    // Localisation
    private String residenceName;

    // CAS APPARTEMENT → référence du bien (ex: "A12")
    // CAS PARTIE_COMMUNE → null
    private String propertyReference;

    // CAS PARTIE_COMMUNE → nom partie commune (ex: "Ascenseur Bloc A")
    // CAS APPARTEMENT → null
    private String commonFacilityName;

    // Date et heure de signalement
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;

    // Statut lisible (ex: "Intervention en cours")
    private String statusLabel;
    private InterventionStatus status;

    // Urgence lisible (ex: "Urgent")
    private String urgencyLabel;
    private UrgencyLevel urgencyLevel;

    // Catégorie = spécialité (ex: "Plomberie")
    private String specialtyName;
    private String specialtyIcon;

    // Photos du problème
    private List<String> photoUrls;

    // Prestataire affecté — null tant qu'aucun devis accepté
    private ProviderInfoDTO selectedProvider;

    // Timeline de suivi
    private List<OwnerTimelineStepDTO> timeline;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime finishedAt;
}
