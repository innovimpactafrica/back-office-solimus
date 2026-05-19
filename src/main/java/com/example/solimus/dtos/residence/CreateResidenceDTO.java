package com.example.solimus.dtos.residence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateResidenceDTO {

    @NotBlank(message = "Le nom de la résidence est obligatoire")
    private String name;

    @NotBlank(message = "L'adresse complète est obligatoire")
    private String fullAddress;

    @NotNull(message = "La latitude est obligatoire")
    private BigDecimal latitude;

    @NotNull(message = "La longitude est obligatoire")
    private BigDecimal longitude;

    private Integer floorCount;
    private Integer apartmentCount;
}
