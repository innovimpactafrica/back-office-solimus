package com.example.solimus.dtos.syndic.charge;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateChargeCallDTO {

    @NotNull(message = "Le numéro de période est obligatoire")
    private Integer periodNumber;

    @NotNull(message = "La date d'envoi est obligatoire")
    private LocalDate sentDate;

    @NotNull(message = "La date d'échéance est obligatoire")
    private LocalDate dueDate;

    // Montants personnalisés pour chaque copropriétaire (mode CUSTOM uniquement)
    private List<CustomCoOwnerAmountDTO> customAmounts;
}
