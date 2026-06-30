package com.example.solimus.dtos.intervention;

import com.example.solimus.dtos.provider.profile.QuoteLineDTO;
import com.example.solimus.enums.QuoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO détail — affiché quand le copropriétaire clique sur un devis.
 * Contient toutes les infos de la carte + contact prestataire + lignes détaillées.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerQuoteDetailDTO {

    private Long id;
    private Long providerId;
    private String providerName;
    private String companyName;
    private String providerPhotoUrl;
    private String providerCity;

    // Contact
    private String providerPhone;
    private String providerEmail;

    // Note et avis
    private double providerRating;
    private int reviewCount;

    // Stats prestataire
    private int interventionCount;     // Ex: 120 interventions
    private int satisfactionRate;      // Ex: 98 (%)
    private String avgInterventionTime; // Ex: "3h"

    // Badges
    private boolean isVerified;
    private boolean isBestOffer;
    private int scoreQualitePrix;

    // Lignes détaillées
    private List<QuoteLineDTO> materialLines;  // Section "Matériels"
    private List<QuoteLineDTO> laborLines;     // Section "Main d'œuvre"

    // Financier
    private BigDecimal laborTotalAmount;
    private BigDecimal materialTotalAmount;
    private BigDecimal totalAmount;

    private String estimatedDelayLabel;
    private String additionalComments;

    private QuoteStatus status;
    private LocalDateTime createdAt;   // "Envoyé le 08 Mai 2026"
}