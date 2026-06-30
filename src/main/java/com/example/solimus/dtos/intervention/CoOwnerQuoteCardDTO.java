package com.example.solimus.dtos.intervention;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO carte — affiché dans la liste des devis côté copropriétaire.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerQuoteCardDTO {

    private Long id;
    private Long providerId;
    private String providerName;       // Nom complet ou nom entreprise
    private String companyName;
    private String providerPhotoUrl;   // Photo/logo du prestataire
    private String providerCity;       // Localisation ou Zone d'intervention

    // Note et avis
    private double providerRating;     // Ex: 4.8
    private long reviewCount;          // Ex: 56

    // Financier
    private BigDecimal totalAmount;
    private String estimatedDelayLabel; // Durée d'estimation

    // Badges
    private boolean isVerified;        // Badge "Prestataire vérifié" (si abonnement actif)
    private boolean isBestOffer;       // Badge "RECOMMANDÉ" (meilleur score)
    private int scoreQualitePrix;     // Ex: 74 (%)

    // Champ interne pour le tri, non exposé au front
    @JsonIgnore
    private double scoreFinal;

}
