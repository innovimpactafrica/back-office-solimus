package com.example.solimus.dtos.syndic.signalement;

import com.example.solimus.enums.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour transformer un signalement en demande de travaux
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertToWorkDTO {

    @NotNull(message = "La spécialité est obligatoire")
    private Long specialtyId;

    @NotNull(message = "La priorité est obligatoire")
    private UrgencyLevel priority;

    @NotBlank(message = "La description des travaux est obligatoire")
    private String workDescription;
}
