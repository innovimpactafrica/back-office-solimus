package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.util.List;

// ===== DTO RÉPONSE PAGINÉE - HISTORIQUE COMPLET DES TRANSACTIONS (onglet Transactions) =====
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletFlowListResponseDTO {

    private long totalCount;
    private List<WalletFlowRowDTO> flows;
    private long currentPage;
    private long totalPages;
}