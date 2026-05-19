package com.example.solimus.dtos.provider;

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
    private String label;        // "Résidence Les Palmiers - Plomberie" ou "Retrait Wave"
    private BigDecimal montant;  // +85 000 ou -50 000
    private TransactionType type; // ENTREE ou SORTIE
    private String statut;       // "Reçu", "En attente", "Effectué"

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate date;
}
