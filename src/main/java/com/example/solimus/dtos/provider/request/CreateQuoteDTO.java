package com.example.solimus.dtos.provider.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// DTO pour la création d'un devis par un prestataire (brouillon ou envoyé)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateQuoteDTO {

    @NotNull(message = "L'ID de la demande est obligatoire")
    private Long interventionRequestId;

    @NotNull(message = "Le délai d'intervention est obligatoire")
    private Long estimatedDelayId;

    private String additionalComments;

    private boolean isDraft; //Brouillon ou pas

    // Toutes les lignes (Matériel + Main d'œuvre)
    private List<QuoteItemDTO> items;
}
