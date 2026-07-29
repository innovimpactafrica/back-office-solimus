package com.example.solimus.dtos.admin.syndic;

import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Une ligne de la liste paginée "Admin > Syndics" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSyndicRowDTO {

    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String companyName;

    private long residencesCount;
    private long coOwnersCount;

    // Formule + statut de l'abonnement en cours (ACTIVE s'il existe, sinon le plus récent) — peut
    // être null si, pour une raison exceptionnelle, aucun abonnement n'a jamais été créé pour ce compte
    private String planName;
    private LocalDateTime expirationDate;
    private SubscriptionStatus status;
    private String statusLabel;
}
