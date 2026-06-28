package com.example.solimus.dtos.provider.request;

import com.example.solimus.enums.ProviderRequestDisplayStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//DTO pour afficher les demandes disponibles pour un prestataire
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProviderRequestSummaryDTO {

    private Long id; // identifiant de la demande, pour ouvrir le détail au clic

    private String title; // "Fuite d'eau"

    private String residenceName; // "Résidence Diana"

    // Le code technique, utile au front pour choisir la couleur du badge
    private ProviderRequestDisplayStatus status;

    // Le texte déjà traduit, utilisable directement si le front ne veut pas gérer la traduction
    private String statusLabel;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
