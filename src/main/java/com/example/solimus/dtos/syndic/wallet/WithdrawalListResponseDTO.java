package com.example.solimus.dtos.syndic.wallet;

import lombok.*;
import java.util.List;

// ===== DTO RÉPONSE PAGINÉE - HISTORIQUE DES RETRAITS =====
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WithdrawalListResponseDTO {

    private long totalCount;
    private List<WithdrawalRowDTO> withdrawals;
    private long currentPage;
    private long totalPages;
}
