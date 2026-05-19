package com.example.solimus.dtos.provider;

import com.example.solimus.enums.QuoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderQuoteListDTO {

    // Bandeau en haut
    private BigDecimal totalMontantValide; // "300 000 FCFA"

    // Liste paginée des devis
    private Page<QuoteSummaryDTO> devis;
}
