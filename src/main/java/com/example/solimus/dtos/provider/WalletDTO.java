package com.example.solimus.dtos.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.domain.Page;
import java.math.BigDecimal;

/**
 * DTO principal représentant le portefeuille électronique d'un prestataire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDTO {
    private BigDecimal soldeDisponible;   // "450 000 FCFA"
    private BigDecimal soldeEnAttente;    // "125 000 FCFA"
    private BigDecimal totalCeMois;       // "450 000 FCFA"
    private Page<WalletTransactionDTO> transactions;
}
