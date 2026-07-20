package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO LIGNE - HISTORIQUE COMPLET DES TRANSACTIONS (onglet Transactions) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletFlowRowDTO {

    private LocalDateTime date;
    private String residenceName;     // nom de la résidence concernée
    private String beneficiaryName;   // copropriétaire (CHARGES) ou prestataire (TRAVAUX), figé au moment de la transaction
    private String category;
    private String categoryLabel;     // "Charges" ou "Travaux", sans suffixe de période
    private String statut;            // "PAYÉ" ou "REÇU"
    private BigDecimal amount;
}