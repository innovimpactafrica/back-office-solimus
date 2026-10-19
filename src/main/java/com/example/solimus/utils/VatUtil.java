package com.example.solimus.utils;

import com.example.solimus.entities.Quote;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * TVA des devis — seule source de vérité du taux (18 %) et des montants HT / TVA / TTC.
 * Le taux n'est jamais saisi par le prestataire ni accepté depuis le client : il est appliqué
 * côté serveur à la création et à chaque modification d'un devis.
 */
public final class VatUtil {

    // Taux de TVA en pourcentage (18 => 18 %)
    public static final BigDecimal VAT_RATE = new BigDecimal("18.00");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private VatUtil() {
    }

    // TVA = HT × taux / 100, arrondie à 2 décimales (HALF_UP)
    public static BigDecimal computeVat(BigDecimal ht, BigDecimal rate) {
        if (ht == null || rate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return ht.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    // --- Lecture null-safe (devis créés avant l'introduction de la TVA : colonnes nulles) ---

    // Montant HT : colonne ht_amount, sinon main-d'œuvre + matériel, sinon le total historique
    public static BigDecimal htOf(Quote quote) {
        if (quote.getHtAmount() != null) {
            return quote.getHtAmount();
        }
        if (quote.getLaborTotalAmount() != null || quote.getMaterialTotalAmount() != null) {
            BigDecimal labor = quote.getLaborTotalAmount() != null ? quote.getLaborTotalAmount() : BigDecimal.ZERO;
            BigDecimal material = quote.getMaterialTotalAmount() != null ? quote.getMaterialTotalAmount() : BigDecimal.ZERO;
            return labor.add(material).setScale(2, RoundingMode.HALF_UP);
        }
        return quote.getTotalAmount();
    }

    // Montant de TVA : colonne tva_amount, sinon 0 (un devis historique n'a pas été facturé avec TVA)
    public static BigDecimal tvaOf(Quote quote) {
        return quote.getTvaAmount() != null
                ? quote.getTvaAmount()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    // Taux appliqué : colonne tax_rate, sinon 0 (devis historique sans TVA)
    public static BigDecimal taxRateOf(Quote quote) {
        return quote.getTaxRate() != null ? quote.getTaxRate() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
