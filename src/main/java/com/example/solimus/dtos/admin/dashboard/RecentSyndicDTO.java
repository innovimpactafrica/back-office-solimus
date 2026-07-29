package com.example.solimus.dtos.admin.dashboard;

import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

// ===== DTO — Bloc 5 : un syndic dans la liste "Derniers syndics" (dashboard admin) =====
// subscriptionStatus renvoie l'énumération réelle du projet (ex: DESACTIVATED pour un compte
// suspendu), pas un libellé personnalisé — cohérent avec le reste de l'API admin.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentSyndicDTO {

    private Long id;
    private String name;
    private String phone;
    private long residencesCount;
    private String planName;
    private SubscriptionStatus subscriptionStatus;
}
