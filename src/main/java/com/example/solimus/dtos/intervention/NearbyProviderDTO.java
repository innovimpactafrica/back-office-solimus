package com.example.solimus.dtos.intervention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NearbyProviderDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String companyName;
    private String specialtyName;
    private Double distanceKm; // La distance calculée par Haversine
    private Double rating;      // Note moyenne 
    private boolean premium;    // Indique si le prestataire a un abonnement Premium actif
}
