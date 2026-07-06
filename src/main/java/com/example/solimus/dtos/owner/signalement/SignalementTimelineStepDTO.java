package com.example.solimus.dtos.owner.signalement;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalementTimelineStepDTO {

    private String label;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime date;

    private boolean completed;

    private String auteurName;  // ex: "Mme. Sophie Sall" — null si événement système
    private String auteurRole;  // ex: "Copropriétaire" ou "Syndic" — null si système
}
