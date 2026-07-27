package com.example.solimus.dtos.admin.subscription;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriptionDuration;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

// DTO — l'admin renouvelle (ou change) l'abonnement d'un syndic, depuis la modale "Renouveler l'abonnement"
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRenewSyndicSubscriptionDTO {

    @NotNull(message = "La formule choisie est obligatoire")
    private Long syndicPlanId;

    @NotNull(message = "La durée est obligatoire")
    private SubscriptionDuration duration;

    // Choisi par l'admin, payeur de ce renouvellement — comme pour la création de compte (SYN-)
    @NotNull(message = "Le mode de paiement est obligatoire")
    private PaymentMethod method;

    // Choisie librement par l'admin — contrairement au self-service, pas de calcul automatique
    @NotNull(message = "La date de début est obligatoire")
    private LocalDate startDate;

    private boolean notifyClient;
    private boolean sendInvoiceEmail;
}