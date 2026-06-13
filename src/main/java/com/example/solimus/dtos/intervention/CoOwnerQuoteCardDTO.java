package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.QuoteStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO carte — affiché dans la liste des devis côté copropriétaire.
 * Contient uniquement les infos visibles sur la carte Figma.
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
    private String providerCity;       // Ex: "Dakar, Sénégal"

    // Note et avis
    private double providerRating;     // Ex: 4.8
    private int reviewCount;           // Ex: 56

    // Financier
    private BigDecimal totalAmount;
    private String estimatedDelayLabel; // Ex: "2 jours"

    // Badges
    private boolean isVerified;        // Badge "Prestataire vérifié"
    private boolean isBestOffer;       // Badge "RECOMMANDÉ" (meilleur score)
    private int scoreQualitePrix;      // Ex: 74 (%)

    // Champ interne pour le tri, non exposé au front
    @JsonIgnore
    private double scoreFinal;

    private QuoteStatus status;
}
