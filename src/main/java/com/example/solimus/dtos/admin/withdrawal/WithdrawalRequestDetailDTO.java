package com.example.solimus.dtos.admin.withdrawal;

import lombok.*;

import java.util.List;

// ===== DTO — Bloc 3 : fiche détail complète d'une demande de retrait (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestDetailDTO {

    private WithdrawalHeaderDTO header;
    private WithdrawalGeneralInfoDTO generalInfo;
    private WithdrawalFinancialAnalysisDTO financialAnalysis;
    private List<RecentWithdrawalRowDTO> recentWithdrawals;
    private List<TimelineStepDTO> timeline;

    // Affichés uniquement s'ils sont renseignés — ne font pas partie de la timeline
    private String adminComment;
    private String receiptUrl;
}
