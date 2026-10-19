package com.example.solimus.entities;

import com.example.solimus.enums.QuoteItemType;
import com.example.solimus.enums.QuoteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.example.solimus.utils.VatUtil;
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
    // Fixée après le premier save, à partir de l'id auto-généré (garanti unique) — voir createQuote()
    @Column(unique = true)
    private String reference;

    // --- Totaux ---
    
    @Column(nullable = false)
    private BigDecimal laborTotalAmount; // Sous-total Main d'œuvre (HT)

    @Column(nullable = false)
    private BigDecimal materialTotalAmount; // Sous-total Matériel (HT)

    @Column(nullable = false)
    private BigDecimal totalAmount; // Grand Total TTC (HT + TVA)

    // Taux de TVA appliqué (VatUtil.VAT_RATE = 18), historisé. Null = devis antérieur à la TVA.
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    // Montant de la TVA (HT × taux), figé à la création / mise à jour du devis
    @Column(name = "tva_amount", precision = 19, scale = 2)
    private BigDecimal tvaAmount;

    // Montant HT (main-d'œuvre + matériel), figé à la création / mise à jour du devis
    @Column(name = "ht_amount", precision = 19, scale = 2)
    private BigDecimal htAmount;

    // --- Détails ---
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estimated_delay_id", nullable = false)
    private EstimatedDelay estimatedDelay; //Durée d'estimation des travaux

    @Column(columnDefinition = "TEXT")
    private String additionalComments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "intervention_request_id", nullable = false)
    private InterventionRequest interventionRequest;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    // Liste unique de toutes les lignes (Matériel + Main d'œuvre)
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuoteItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Calcule automatiquement les sous-totaux HT, le montant de TVA et le total TTC.
     *
     * Règle cible :
     *   HT   = main-d'œuvre + matériaux
     *   TVA  = HT × taux (taux exprimé en pourcentage, ex: 18 pour 18 %)
     *   TTC  = HT + TVA
     *
     * Les montants sont arrondis avec setScale(2, RoundingMode.HALF_UP), cohérent avec les
     * autres montants de la plateforme. Le calcul est null-safe : le TTC envoyé par le client
     * n'est jamais utilisé comme source de vérité, il est toujours recalculé côté backend.
     */
    @PrePersist
    @PreUpdate
    public void calculateTotals() {
        BigDecimal ZERO = BigDecimal.ZERO;

        // 1. Sous-total Matériel (HT)
        this.materialTotalAmount = items.stream()
                .filter(item -> item.getType() == QuoteItemType.MATERIAL)
                .map(QuoteItem::getTotalPrice)
                .reduce(ZERO, BigDecimal::add);

        // 2. Sous-total Main d'œuvre (HT)
        this.laborTotalAmount = items.stream()
                .filter(item -> item.getType() == QuoteItemType.LABOR)
                .map(QuoteItem::getTotalPrice)
                .reduce(ZERO, BigDecimal::add);

        // 3. Montant HT global (main-d'œuvre + matériaux)
        BigDecimal ht = (this.materialTotalAmount == null ? ZERO : this.materialTotalAmount)
                .add(this.laborTotalAmount == null ? ZERO : this.laborTotalAmount);
        this.htAmount = ht.setScale(2, RoundingMode.HALF_UP);

        // 4. Montant de la TVA = HT × taux / 100. Le taux est posé par le service (VatUtil.VAT_RATE) à la
        //    création / modification par le prestataire ; null uniquement pour les devis historiques
        //    (avant TVA), qui gardent ainsi leur total d'origine à chaque @PreUpdate.
        this.tvaAmount = VatUtil.computeVat(this.htAmount, this.taxRate);

        // 5. Total TTC = HT + TVA
        this.totalAmount = this.htAmount.add(this.tvaAmount);
    }
}
