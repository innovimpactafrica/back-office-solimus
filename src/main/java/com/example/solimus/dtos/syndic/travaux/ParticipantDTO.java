package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

//DTO d'un participant affiché sur la fiche incident
@Data
@Builder
public class ParticipantDTO {
    private String role; // "Copropriétaire", "Syndic", "Prestataire"
    private String name;
    private String photoUrl;
}
