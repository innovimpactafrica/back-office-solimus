package com.example.solimus.dtos.admin.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO envoyé par l'admin pour créer ou mettre à jour la formule prestataire.
 * Utilisé à la fois pour la création initiale et la modification.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderPlanRequestDTO {

    @NotBlank(message = "Le nom de la formule est obligatoire")
    private String name;

    @Size(max = 250, message = "La description ne doit pas dépasser 250 caractères")
    private String description;

    @NotNull(message = "Le prix mensuel est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix mensuel doit être supérieur à 0")
    private BigDecimal monthlyPrice;

    // Optionnel — l'admin peut ne pas proposer de prix annuel
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix annuel doit être supérieur à 0")
    private BigDecimal yearlyPrice;
}