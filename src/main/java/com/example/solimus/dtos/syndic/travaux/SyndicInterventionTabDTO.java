package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

//DTO de l'onglet "Intervention" (onglet 3, syndic)
@Data
@Builder
public class SyndicInterventionTabDTO {
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String dureeEstimee; // depuis le devis accepté

    private String providerReport; // dernier InterventionComment du prestataire
    private List<String> photosBefore; // photoUrls
    private List<String> photosAfter; // workPhotoUrls
}
