package com.example.solimus.entities;

import com.example.solimus.enums.QuoteItemType;
import com.example.solimus.enums.QuoteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ============================================================
// Quote.java
// Devis détaillé avec séparation Matériel / Main d'œuvre.
// ============================================================
@Entity
@Table(name = "quotes")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Identification ---
    @Column(nullable = false, unique = true)
    private String reference;

    // --- Totaux ---
    
    @Column(nullable = false)
    private BigDecimal laborTotalAmount; // Sous-total Main d'œuvre

    @Column(nullable = false)
    private BigDecimal materialTotalAmount; // Sous-total Matériel

    @Column(nullable = false)
    private BigDecimal totalAmount; // Grand Total TTC

    // --- Détails ---
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimated_delay_id", nullable = false)
    private EstimatedDelay estimatedDelay;

    @Column(columnDefinition = "TEXT")
    private String additionalComments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_request_id", nullable = false)
    private InterventionRequest interventionRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    // Liste unique de toutes les lignes (Matériel + Main d'œuvre)
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuoteItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Calcule automatiquement les sous-totaux et le total TTC.
     */
    @PrePersist
    @PreUpdate
    public void calculateTotals() {
        // 1. Sous-total Matériel
        this.materialTotalAmount = items.stream()
                .filter(item -> item.getType() == QuoteItemType.MATERIAL)
                .map(QuoteItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Sous-total Main d'œuvre
        this.laborTotalAmount = items.stream()
                .filter(item -> item.getType() == QuoteItemType.LABOR)
                .map(QuoteItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 3. Total TTC
        this.totalAmount = materialTotalAmount.add(laborTotalAmount);
    }
}
