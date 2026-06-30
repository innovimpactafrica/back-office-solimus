package com.example.solimus.dtos.owner.travaux;

import com.example.solimus.dtos.provider.profile.QuoteLineDTO;
import com.example.solimus.enums.QuoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

//DTO détails d'un dévis spécifique
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerQuoteDetailDTO {

    // =========================================================================
    // STATUT ET MONTANT (bloc en-tête maquette)
    // =========================================================================
    private QuoteStatus quoteStatus;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;       // "Envoyé le 08 Mai 2026"

    // =========================================================================
    // INFOS PRESTATAIRE
    // =========================================================================
    private String providerName;           // prénom + nom
    private String companyName;            // Plomberie Sénégal
    private String providerPhotoUrl;
    private String providerPhone;          // +221 33 800 00 00
    private String providerEmail;          // contact@plomberie-sn.com
    private String providerCity;           // interventionZone → "Dakar, Sénégal"

    // =========================================================================
    // STATISTIQUES PRESTATAIRE
    // =========================================================================
    private Double rating;                 // 4.8 — depuis ProviderProfile.rating
    private Integer reviewCount;           // 56
    private Integer interventionCount;     // 120
    private Integer satisfaction;          // 98% — % d'avis >= 4 étoiles
    private Double averageTimeHours;       // 3.0 — moyenne finishedAt - startedAt

    // =========================================================================
    // DÉTAIL FINANCIER
    // =========================================================================
    private List<QuoteLineDTO> materiaux;
    private BigDecimal sousTotalMateriaux;

    private List<QuoteLineDTO> mainOeuvre;
    private BigDecimal sousTotalMainOeuvre;

    private BigDecimal totalTTC;
}
