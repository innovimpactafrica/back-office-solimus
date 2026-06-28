package com.example.solimus.dtos.provider.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;

//DTO POUR LA LISTE DES DEVIS D'UN PRESTATAIRE
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderQuoteListDTO {

    // Bandeau en haut
    private BigDecimal totalValidAmount;

    // Liste paginée des devis
    private Page<QuoteSummaryDTO> devis;
}
