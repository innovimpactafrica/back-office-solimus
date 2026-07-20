package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.math.BigDecimal;

// ===== DTO KPIS - ONGLET RETRAITS =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalKpiDTO {

    private BigDecimal soldeDisponible;   // même calcul que Vue d'ensemble
    private BigDecimal enAttente;         // somme des retraits PENDING (sans limite de période ici)
    private BigDecimal retraitsTotaux;    // somme des retraits COMPLETED, depuis toujours
}
