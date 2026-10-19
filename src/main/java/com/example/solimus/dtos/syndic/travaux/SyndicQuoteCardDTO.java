package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.enums.QuoteStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO d'une ligne de la liste des devis reçus (onglet 2, syndic)
@Data
@Builder
public class SyndicQuoteCardDTO {
    private Long id;
    private String reference;
    private String providerName;
    private LocalDateTime createdAt;
    private BigDecimal totalAmount;

    // ===== TVA / décomposition du montant =====
    private BigDecimal taxRate;   // Taux TVA en % (ex: 18)
    private BigDecimal htAmount;  // Montant HT
    private BigDecimal tvaAmount; // Montant de la TVA

    private QuoteStatus status;
    private boolean isRetained; // true si c'est le devis accepté
}
