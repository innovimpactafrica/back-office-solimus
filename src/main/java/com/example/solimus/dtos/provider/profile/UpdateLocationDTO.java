package com.example.solimus.dtos.provider.profile;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

//DTO pour la mise à jour de la localisation d'un prestataire   
@Data
public class UpdateLocationDTO {

    @NotNull(message = "La latitude est obligatoire")
    private BigDecimal latitude; // position GPS actuelle envoyée par le front/app

    @NotNull(message = "La longitude est obligatoire")
    private BigDecimal longitude; // position GPS actuelle envoyée par le front/app
}
