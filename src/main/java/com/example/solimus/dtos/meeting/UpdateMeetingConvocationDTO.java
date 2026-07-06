package com.example.solimus.dtos.meeting;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

//Etape 2 création AG
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeetingConvocationDTO {

    @NotNull(message = "La date d'envoi des convocations est obligatoire")
    private LocalDate convocationSentDate;

    private String convocationMessage;

    private Boolean sendByEmail;
    private Boolean sendByPlatformNotification;
    private Boolean sendBySms;
}
