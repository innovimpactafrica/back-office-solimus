package com.example.solimus.entities;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.PaymentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence unique du paiement (ex: PAY-2026-001)
    @Column(unique = true, nullable = false)
    private String reference;

    // La demande d'intervention concernée
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_request_id", nullable = false)
    private InterventionRequest interventionRequest;

    // Le prestataire qui reçoit le paiement
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    // Celui qui effectue le paiement (syndic OU copropriétaire)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_initiator_id", nullable = false)
    private User paymentInitiator;

    // Montant de ce paiement
    @Column(nullable = false)
    private BigDecimal amount;

    // Type : ACOMPTE ou SOLDE
    @Enumerated(EnumType.STRING)
    private PaymentType type;

    // Moyen de paiement
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    // Statut du paiement
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
