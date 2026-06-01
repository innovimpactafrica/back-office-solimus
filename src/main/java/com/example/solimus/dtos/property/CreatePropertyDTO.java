package com.example.solimus.dtos.property;

import com.example.solimus.enums.PropertyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePropertyDTO {

    // La référence est auto-générée, plus besoin de la saisir
    private Integer floor;

    private Double area;

    @NotNull(message = "Le type de bien est obligatoire")
    private PropertyType type;

    @NotNull(message = "L'ID de la résidence est obligatoire")
    private Long residenceId;

    // Un seul propriétaire par bien
    private Long ownerId;
}
