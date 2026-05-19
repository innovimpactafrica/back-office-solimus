package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.SubscriptionPaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence unique : PAY-2026-05
    @Column(unique = true, nullable = false)
    private String reference;

    // L'abonnement concerné
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    // Montant payé
    private BigDecimal montant;

    // Moyen de paiement
    @Enumerated(EnumType.STRING)
    private PaymentMethod moyenPaiement;

    // Statut
    @Enumerated(EnumType.STRING)
    private SubscriptionPaymentStatus statut; // PAYE, ECHOUE

    // Période concernée : "Mai 2026"
    private String periode;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
