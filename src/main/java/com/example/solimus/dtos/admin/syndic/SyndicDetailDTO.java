package com.example.solimus.dtos.admin.syndic;

import lombok.*;

// ===== DTO — Fiche détail complète d'un syndic (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicDetailDTO {

    private Long userId;
    private SyndicGeneralInfoDTO generalInfo;
    private SyndicDetailKpiDTO kpis;
    private SyndicSubscriptionInfoDTO subscription;
}
