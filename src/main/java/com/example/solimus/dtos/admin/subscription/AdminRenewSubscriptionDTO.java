package com.example.solimus.dtos.admin.subscription;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriptionDuration;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

// DTO — l'admin renouvelle (ou change) l'abonnement d'un abonné (syndic OU prestataire, voir
// subscriberType dans le paramètre de requête), depuis la modale "Renouveler l'abonnement"
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRenewSubscriptionDTO {

    @NotNull(message = "La formule choisie est obligatoire")
    private Long planId;

    @NotNull(message = "La durée est obligatoire")
    private SubscriptionDuration duration;

    // Choisi par l'admin, payeur de ce renouvellement
    @NotNull(message = "Le mode de paiement est obligatoire")
    private PaymentMethod method;

    // Choisie librement par l'admin — pas de calcul automatique
    @NotNull(message = "La date de début est obligatoire")
    private LocalDate startDate;

    private boolean notifyClient;
    private boolean sendInvoiceEmail;
}
