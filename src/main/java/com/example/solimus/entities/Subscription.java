package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
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
public class Subscription {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status; // ACTIVE, EXPIRED, CANCELLED

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