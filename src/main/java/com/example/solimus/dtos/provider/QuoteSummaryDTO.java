package com.example.solimus.dtos.provider;

import com.example.solimus.enums.QuoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteSummaryDTO {
    private Long id;                // ID numérique du devis
    private String reference;       // "DEV-584729"
    private String titre;           // "Réparation fuite d'eau"
    private String residenceName;   // "Résidence Les Palmiers"
    private String appartement;     // "Apt 205"

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate date;
    private BigDecimal montant;
    private QuoteStatus statut;     // ACCEPTE, EN_ATTENTE, REFUSE
    private String estimatedDelayLabel; // "1-2 jours", "3-5 jours", etc.
}
