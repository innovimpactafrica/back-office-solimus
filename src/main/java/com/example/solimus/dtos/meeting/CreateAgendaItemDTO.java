package com.example.solimus.dtos.meeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour créer un point de l'ordre du jour lors de la création d'une réunion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAgendaItemDTO {

    @NotNull(message = "L'ordre est obligatoire")
    private Integer orderIndex;

    @NotBlank(message = "Le titre est obligatoire")
    private String title;
}
