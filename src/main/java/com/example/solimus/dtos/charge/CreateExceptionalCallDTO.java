package com.example.solimus.dtos.charge;

import com.example.solimus.enums.ExceptionalCallCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExceptionalCallDTO {

    @NotNull(message = "L'ID de la résidence est obligatoire")
    private Long residenceId;

    @NotNull(message = "La catégorie est obligatoire")
    private ExceptionalCallCategory category;

    @NotBlank(message = "L'objet des travaux est obligatoire")
    private String title;

    private String description;
}
