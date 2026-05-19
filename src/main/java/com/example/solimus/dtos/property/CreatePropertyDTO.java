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

    @NotBlank(message = "La référence du bien est obligatoire")
    private String reference;

    private Integer floor;

    private Double area;

    @NotNull(message = "Le type de bien est obligatoire")
    private PropertyType type;

    @NotNull(message = "L'ID de la résidence est obligatoire")
    private Long residenceId;

    // Un seul propriétaire par bien
    private Long ownerId;
}
