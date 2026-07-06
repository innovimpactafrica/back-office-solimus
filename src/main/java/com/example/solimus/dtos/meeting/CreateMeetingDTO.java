package com.example.solimus.dtos.meeting;

import com.example.solimus.enums.MeetingType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO pour créer une nouvelle réunion.
 * Contient les informations de base de la réunion (titre, date, lieu, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetingDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String description;

    @NotNull(message = "Le type est obligatoire")
    private MeetingType type;

    @NotNull(message = "La date de réunion est obligatoire")
    @Future(message = "La date de réunion doit être dans le futur")
    private LocalDate meetingDate;

    @NotNull(message = "L'heure de début est obligatoire")
    private LocalTime startTime;

    // Optionnel — la maquette étape 1 n'a qu'un seul champ heure
    private LocalTime endTime;

    @NotBlank(message = "Le lieu est obligatoire")
    private String location;

    @NotNull(message = "L'ID de la résidence est obligatoire")
    private Long residenceId;
}
