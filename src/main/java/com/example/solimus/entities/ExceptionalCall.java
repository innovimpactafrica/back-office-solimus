package com.example.solimus.entities;

import com.example.solimus.enums.ExceptionalCallCategory;
import com.example.solimus.enums.ExceptionalCallStatus;
import com.example.solimus.enums.RepartitionMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * APPEL DE CHARGES EXCEPTIONNEL
 * ============================================================================
 * Charge ponctuelle (ex: ravalement, réfection toiture), distincte du système
 * ChargeCall/Budget qui gère les charges récurrentes trimestrielles/mensuelles.
 * Rempli progressivement à travers un formulaire en 3 sections — chaque section
 * complète cette même entité via son id, rien n'est perdu en cours de route.
 */
@Entity
@Table(name = "exceptional_calls")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionalCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;

    // ---- Section 1 : Informations générales ----
    @Enumerated(EnumType.STRING)
    private ExceptionalCallCategory category;

    @Column(nullable = false)
    private String title; // "Objet des travaux"

    @Column(unique = true)
    private String reference; // Référence unique de l'appel (ex: CHG-2026-06-A12)

    @Column(columnDefinition = "TEXT")
    private String description;

    // ---- Section 2 : Informations financières ----
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private RepartitionMode repartitionMode;

    // ---- Section 3 : Validation & Documents ----
    private Boolean requiresAgValidation;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionalCallStatus status;

    @OneToMany(mappedBy = "exceptionalCall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExceptionalCallDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "exceptionalCall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExceptionalCallItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
}
