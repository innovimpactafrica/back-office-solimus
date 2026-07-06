package com.example.solimus.dtos.syndic.signalement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResoudreSignalementDTO {
    private String noteCloture;
}
