package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.QuoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO utilisé par le Syndic pour visualiser et comparer les devis reçus.
 * Aligné sur le design Figma (Cartes de comparaison).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyndicQuoteDTO {
    private Long id;
    
    // Informations Prestataire
    private Long providerId;
    private String providerName;
    private String companyName;
    // Champs pour le rapport qualité/prix
    private double providerRating;    // note moyenne réelle du prestataire
    private double scoreFinal;        // score brut calculé
    private int scoreQualitePrix;     // score en pourcentage (ex: 74%)
    private boolean isBestOffer;      // true = badge "Meilleur rapport"
    
    // Détails financiers
    private BigDecimal laborTotalAmount;   // Sous-total Main d'œuvre
    private BigDecimal materialTotalAmount; // Sous-total Matériel
    private BigDecimal totalAmount;         // Total global
    
    // Logistique
    private String estimatedDelayLabel;     // Ex: "1 j", "3 j"
    private String additionalComments;
    
    private QuoteStatus status;
    private LocalDateTime createdAt;
}
