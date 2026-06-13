package com.example.solimus.dtos.intervention;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateQuoteDTO {

    @NotNull(message = "L'ID de la demande est obligatoire")
    private Long interventionRequestId;

    @NotNull(message = "Le délai d'intervention est obligatoire")
    private Long estimatedDelayId;

    private String additionalComments;

    private boolean isDraft;

    // Toutes les lignes (Matériel + Main d'œuvre)
    private List<QuoteItemDTO> items;
}
