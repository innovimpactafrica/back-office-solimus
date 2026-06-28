package com.example.solimus.dtos.syndic.travaux;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de création d'un avis après une intervention terminée.
 * Utilisé par le copropriétaire ET le syndic.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateReviewDTO {

    @NotNull(message = "La note est obligatoire")
    @Min(value = 1, message = "La note minimale est 1 étoile")
    @Max(value = 5, message = "La note maximale est 5 étoiles")
    private Integer rating;

    // Commentaire optionnel
    private String comment;
}
