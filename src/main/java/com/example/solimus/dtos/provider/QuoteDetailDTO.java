package com.example.solimus.dtos.provider;

import com.example.solimus.enums.QuoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteDetailDTO {

    private String reference;           // "DEV-584729"
    private String titre;
    private QuoteStatus statut;
    private BigDecimal montantTotal;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dateEnvoi;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dateValidation;

    // Informations client
    private String clientNom;
    private String clientTelephone;
    private String clientEmail;
    private String clientAdresse;

    // Matériels (liste des lignes)
    private List<QuoteLineDTO> materiaux;
    private BigDecimal sousTotalMateriaux;

    // Main d'œuvre (liste des lignes)
    private List<QuoteLineDTO> mainOeuvre;
    private BigDecimal sousTotalMainOeuvre;

    // Total
    private BigDecimal totalTTC;

    // Notes
    private String notes;

    // Délai estimé
    private String estimatedDelayLabel; // "1-2 jours", "3-5 jours", etc.
}
