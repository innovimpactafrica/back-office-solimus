package com.example.solimus.dtos.admin.provider;

import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Une ligne de la liste paginée "Admin > Prestataires" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProviderRowDTO {

    private Long userId;
    private String companyName;
    private String responsibleName;
    private String phone;
    private String specialtyName;
    private long missionsCompletedCount;

    // Formule + statut de l'abonnement en cours (ACTIVE s'il existe, sinon le plus récent) — peut
    // être null si aucun abonnement n'a jamais été créé pour ce compte
    private String planName;
    private LocalDateTime expirationDate;
    private SubscriptionStatus status;
    private String statusLabel;

    private String city;
    private String country;
}
