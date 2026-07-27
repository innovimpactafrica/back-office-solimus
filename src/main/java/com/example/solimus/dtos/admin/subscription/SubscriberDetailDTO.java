package com.example.solimus.dtos.admin.subscription;

import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO — Page détail d'un abonné (Syndic ou Prestataire), ouverte via l'icône œil de la liste =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberDetailDTO {

    // ----- En-tête -----
    private String profilePhotoUrl;
    private String clientName;         // société si renseignée, sinon prénom + nom (comme dans la liste)
    private String city;
    private String country;
    private SubscriberType subscriberType;
    private String entityTypeLabel;    // "Syndic de Copropriété" / "Prestataire de services" selon le type d'abonné  subscriberType
    private SubscriptionStatus status;
    private String statusLabel;
    private LocalDateTime memberSince; // date de création du compte

    // ----- Informations Générales -----
    private String responsibleName;    // prénom + nom du responsable, même si une société existe
    private String planName;
    private BigDecimal amount;         // montant réellement payé pour l'abonnement en cours
    private String durationLabel;      // "1 mois" / "12 mois"
    private LocalDateTime subscriptionDate;  // date de la toute première souscription (pas celle en cours)
    private LocalDateTime expirationDate;    // fin de l'abonnement en cours
    private String paymentMethodLabel; // moyen de paiement de l'abonnement en cours

    // ----- Cycle de Vie -----
    private SubscriberLifecycleDTO lifecycle;
}