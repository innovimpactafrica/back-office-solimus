package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.math.BigDecimal;

// ===== DTO KPIS - PORTEFEUILLE FINANCIER SYNDIC (Vue d'ensemble) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletKpiDTO {

    private BigDecimal soldeDisponible;        // Trésorerie = somme de toutes les transactions
    private BigDecimal soldeVariationPercent;   // variation % vs fin du mois précédent

    private BigDecimal chargesCollectees;       // somme CHARGES du trimestre en cours

    private BigDecimal paiementPrestataires;    // somme TRAVAUX (valeur absolue) du mois en cours
    private long paiementPrestatairesCount;      // nombre de transactions TRAVAUX du mois en cours

    private BigDecimal retraitsEnAttente;       // somme des SyndicWithdrawalRequest PENDING du mois en cours
}