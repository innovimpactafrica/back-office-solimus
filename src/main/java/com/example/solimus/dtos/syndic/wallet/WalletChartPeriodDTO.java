package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletChartPeriodDTO {

    private String label;                  // "JAN", "FÉV", "MAR"...
    private BigDecimal recettesCharges;     // somme CHARGES du mois
    private BigDecimal depensesPrestataires; // somme TRAVAUX + RETRAIT (valeur absolue) du mois
}
