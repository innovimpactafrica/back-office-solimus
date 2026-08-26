package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.WalletTransactionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionDTO {

    private Long id;

    private String label;

    // Nom de la résidence concernée — utile surtout en vue globale (toutes résidences mélangées,
    // sans filtre residenceId). Null uniquement si la transaction n'a aucune résidence rattachée.
    private String residenceName;

    // Copropriétaire payeur (CHARGES) ou prestataire payé (TRAVAUX) — null pour RETRAIT
    private String payerOrPayeeName;

    // Lot(s) concerné(s), séparés par virgule — uniquement pour CHARGES, null sinon
    private String propertyReference;

    private String reference;

    private LocalDateTime transactionDate;

    private BigDecimal amount; // signé : négatif = dépense, positif = entrée

    private String mode;

    private WalletTransactionCategory category;
}
