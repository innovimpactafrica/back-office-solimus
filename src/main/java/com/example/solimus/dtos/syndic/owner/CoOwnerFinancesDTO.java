package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

//DTO pour l'onglet Finances du détail copropriétaire (par résidence)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerFinancesDTO {

    // Année en cours utilisée pour tous les calculs ci-dessous — à afficher dynamiquement par le
    // front ("Montant restant — {year}"), jamais codée en dur. Toujours renvoyée, même si
    // budgetExists est false, pour que le front sache quelle année afficher dans son message.
    private Integer year;

    // true si un Budget existe pour (residenceId, year) — si false, tous les montants/taux ci-dessous
    // sont null (pas 0) : le front affiche "Budget {year} non généré" plutôt qu'un chiffre trompeur
    private Boolean budgetExists;

    // 4 cards KPI — null si budgetExists est false
    private BigDecimal monthlyChargeAmount;   // charges annuelles de l'année / 12
    private BigDecimal quarterlyChargeAmount; // charges annuelles de l'année / 4
    private BigDecimal remainingAmount;       // charges non soldées de l'année (totalDue - paidAmount), pénalité incluse
    private BigDecimal remainingPenaltyAmount; // part de remainingAmount venant uniquement de la pénalité
    private Integer settlementRate;           // paidCallsCount / totalCallsCount x 100
    private Integer paidCallsCount;           // appels de charges de l'année déjà soldés (status = PAID)
    private Integer totalCallsCount;          // appels de charges émis cette année, tous statuts

    // Historique des paiements (graphique mensuel)
    private List<MonthlyPaymentDTO> monthlyPayments;

    // Tableau des appels de charges
    private List<ChargeCallRowDTO> chargeCalls;
}
