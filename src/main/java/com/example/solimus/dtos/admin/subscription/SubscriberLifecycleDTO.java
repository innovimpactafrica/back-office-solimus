package com.example.solimus.dtos.admin.subscription;

import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Cycle de vie de l'abonnement d'un abonné (4 cases fixes de la page détail) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberLifecycleDTO {

    // Date de la toute première souscription jamais faite par cet abonné (géré par admin)
    private LocalDateTime initialSubscriptionDate;

    // Dernière fois qu'il a repris la MÊME formule que celle d'avant — null si jamais renouvelé
    private LocalDateTime renewalDate;

    // Dernière fois qu'il est passé à une formule DIFFÉRENTE de celle d'avant — null si jamais changé
    private LocalDateTime planChangeDate;

    // Date de fin de l'abonnement en cours
    private LocalDateTime expectedExpirationDate;
}