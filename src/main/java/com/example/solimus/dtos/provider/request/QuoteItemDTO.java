package com.example.solimus.dtos.provider.request;

import com.example.solimus.enums.QuoteItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO représentant une ligne de devis (matériel ou main d'œuvre) — montants HT, la TVA est calculée côté serveur
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuoteItemDTO {

    @NotBlank(message = "La description de la ligne est obligatoire")
    @Schema(example = "Remplacement du robinet")
    private String description;

    @NotNull(message = "La quantité est obligatoire")
    @Positive(message = "La quantité doit être supérieure à 0")
    @Schema(example = "2")
    private Integer quantity;

    @NotNull(message = "Le prix unitaire est obligatoire")
    @PositiveOrZero(message = "Le prix unitaire ne peut pas être négatif")
    @Schema(description = "Prix unitaire HT", example = "15000")
    private BigDecimal unitPrice; // Prix unitaire HT

    @NotNull(message = "Le type de ligne est obligatoire (MATERIAL ou LABOR)")
    @Schema(example = "MATERIAL")
    private QuoteItemType type; // MATERIAL ou LABOR
}
