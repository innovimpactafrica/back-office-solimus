package com.example.solimus.dtos.provider.travaux;

import com.example.solimus.enums.InterventionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Carte d'un travail affiché dans l'onglet "Travaux" du prestataire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderTravauxDTO {

    // Identifiant de l'intervention → pour ouvrir le détail au clic
    private Long id;

    // Titre de l'intervention (ex: "Fuite d'eau")
    private String title;

    // Nom de la résidence (ex: "Résidence Diana")
    private String residenceName;

    // Statut brut → utile au front pour la couleur du badge
    private InterventionStatus status;

    // Libellé du statut déjà traduit (ex: "En cours", "Terminé", "Clôturé")
    private String statusLabel;

    // Date de création de la demande (ex: "10/11/2024 14:30")
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
