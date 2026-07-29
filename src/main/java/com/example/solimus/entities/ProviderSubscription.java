package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionDuration;
import com.example.solimus.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// =============================================================================
//
//  SUBSCRIPTION — Abonnement Premium d'un prestataire
//
//  Règle métier : binaire.
//  - status = ACTIVE et non expiré  → accès total à la plateforme
//  - sinon (EXPIRED, jamais souscrit) → accès uniquement inscription/connexion
//
//  Le montant payé (amountPaid) est figé au moment du paiement, même si
//  l'admin modifie le prix du ProviderPlan plus tard — on garde l'historique
//  réel de ce qui a été payé.
//
// =============================================================================
@Entity
@Table(name = "subscriptions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Le prestataire titulaire de cet abonnement.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    /**
     * La formule au moment de la souscription (pour garder la trace
     * du nom/description affichés à ce moment-là, même si modifiés depuis).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_plan_id", nullable = false)
    private ProviderPlan providerPlan;

    // Le payeur réel — le prestataire lui-même en self-service, ou l'admin s'il renouvelle à sa place
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "initiated_by_id")
    private User initiatedBy;

    // État de vie de l'abonnement (ACTIVE/EXPIRED/CANCELLED/PENDING/FAILED/DESACTIVATED)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // Résultat du paiement de CETTE tentative précise — posé une seule fois au callback TouchPay
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    // Durée choisie au moment du paiement (MONTHLY/YEARLY)
    @Enumerated(EnumType.STRING)
    @Column(name = "duration", nullable = false)
    private SubscriptionDuration duration;

    /**
     * Montant réellement payé, figé au moment du paiement TouchPay.
     */
    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    /**
     * Référence transaction TouchPay (préfixe SUB- comme déjà défini).
     */
    @Column(name = "transaction_ref", unique = true)
    private String transactionRef;

    /**
     * Méthode de paiement choisie par le prestataire (Wave, Orange Money,Virement bancaire...)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod method;

    // Choix de l'admin au moment d'un renouvellement manuel, à respecter au callback TouchPay
    // (qui arrive plus tard, de façon asynchrone)
    @Column(name = "notify_client")
    private Boolean notifyClient = false;

    @Column(name = "send_invoice_email")
    private Boolean sendInvoiceEmail = false;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Vérifie si l'abonnement est actuellement valide (statut actif
     * ET date d'expiration non dépassée).
     */
    public boolean isCurrentlyActive() {
        return status == SubscriptionStatus.ACTIVE
                && endDate != null
                && endDate.isAfter(LocalDateTime.now());
    }
}