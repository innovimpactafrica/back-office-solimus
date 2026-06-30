package com.example.solimus.dtos.provider.wallet;

import com.example.solimus.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO représentant une transaction individuelle (Entrée ou Sortie) dans l'historique du Wallet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionDTO {
    private String label;        // Titre
    private BigDecimal amount;  // Montant
    private TransactionType type; // ENTREE ou SORTIE
    private String status;       // "Reçu", "En attente", "Effectué"

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate date;
}
