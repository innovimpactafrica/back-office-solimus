package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.util.List;

// ===== DTO RÉPONSE - APERÇU "DERNIERS FLUX" (Vue d'ensemble Wallet, limité à 5) =====
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletFlowOverviewResponseDTO {
    private List<WalletFlowRowDTO> flows;
}