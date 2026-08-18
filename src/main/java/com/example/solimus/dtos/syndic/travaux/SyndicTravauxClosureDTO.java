package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

// DTO du détail de clôture d'une intervention (flux manuel)
@Data
@Builder
public class SyndicTravauxClosureDTO {
    private LocalDateTime startedAt;   // Date prise en charge (début des travaux)
    private LocalDateTime validatedAt; // Date de la clôture
    private String closingNote;        // Note de clôture laissée par le syndic
    private List<String> photosBefore; // photoUrls — photos avant travaux
    private List<String> photosAfter;  // workPhotoUrls — photos après travaux, ajoutées à la clôture
}