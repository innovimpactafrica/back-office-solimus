package com.example.solimus.dtos.provider.profile;

import com.example.solimus.enums.QuoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

//DTO POUR LE SOMMAIRE D'UN DEVIS
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteSummaryDTO {
    private Long id;
    private String reference;
    private String titre;
    private String residenceName;
    private String appartement;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate date;
    private BigDecimal montant;
    private QuoteStatus statut;
}
