package com.example.solimus.dtos.syndic.settings;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO Input
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatePropertyTypeDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    private String description;
}
