package com.example.solimus.dtos.syndic.meeting;

import com.example.solimus.enums.MeetingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

//DTO de création d'une réunion (AG)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeetingDTO {

    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId;

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    // Ordinaire par défaut si non précisé
    private MeetingType type;

    @NotNull(message = "La date est obligatoire")
    private LocalDate meetingDate;

    @NotNull(message = "L'heure de début est obligatoire")
    private LocalTime startTime;

    private LocalTime endTime;

    private String location;

    private LocalDate convocationSentDate;

    @NotBlank(message = "Le message de convocation est obligatoire")
    private String convocationMessage;

    private Boolean sendByEmail;
    private Boolean sendByPlatformNotification;
    private Boolean sendBySms;

    // Ordre du jour — liste de points avec titre et description optionnelle
    private List<AgendaItemDTO> agendaItems;

    // true = publier directement (UPCOMING), false = enregistrer en brouillon (DRAFT)
    @NotNull(message = "Vous devez préciser si c'est un brouillon ou une publication")
    private Boolean publish;
}
