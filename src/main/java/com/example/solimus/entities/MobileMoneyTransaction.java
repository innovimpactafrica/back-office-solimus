package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Suivi d'une transaction Wave / Orange Money initiée via l'API TouchPay (GU).
 * Sert à la réconciliation : tant qu'elle est INITIATED, le statut est relu chez GU (source de
 * vérité) jusqu'à SUCCEED ou FAILED, puis la confirmation métier est appliquée UNE seule fois.
 * Colonnes texte (pas de @Enumerated) : un ENUM MySQL ne suit pas l'ajout de valeurs (ddl-auto=update).
 */
@Entity
@Table(name = "mobile_money_transactions",
        indexes = @Index(name = "idx_mmt_status_created", columnList = "status, created_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Référence interne (SYR-, CPY-, ...) = idFromClient envoyé à GU
    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    // WAVE ou ORANGE_MONEY
    @Column(nullable = false, length = 20)
    private String operator;

    @Column(name = "id_from_gu", length = 64)
    private String idFromGU;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    // INITIATED tant que GU n'a pas tranché, puis SUCCEED ou FAILED (statut GU)
    @Column(nullable = false, length = 20)
    private String status;

    // URLs de retour fournies par l'application mobile — la redirection finale se fait vers elles,
    // jamais vers une URL reçue en paramètre (pas de redirection ouverte)
    @Column(name = "return_url", length = 500)
    private String returnUrl;

    @Column(name = "cancel_url", length = 500)
    private String cancelUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Dernière interrogation du statut chez GU
    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    // Date à laquelle la confirmation métier (payé / échoué) a été appliquée — null = pas encore
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;
}
