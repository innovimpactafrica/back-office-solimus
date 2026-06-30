package com.example.solimus.dtos.provider.wallet;

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
    private BigDecimal availableBalance;   // Solde disponible
    private BigDecimal pendingBalance;    // Solde en attente de validation par Admin
    private BigDecimal totalThisMonth;    // Total reçu ce mois
    private Page<WalletTransactionDTO> transactions; //Page de toutes les transactions effectuées
}
