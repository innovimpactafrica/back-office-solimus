package com.example.solimus.dtos.syndic.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaItemDTO {

    private String title;
    private String description; // optionnelle

    @Builder.Default
    private Boolean requiresResolution = false; // coché par le syndic : ce point nécessite-t-il une décision formelle ?
}
