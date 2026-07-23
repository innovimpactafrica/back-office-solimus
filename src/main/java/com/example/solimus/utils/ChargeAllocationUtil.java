package com.example.solimus.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitaire de répartition de charges par tantième — partagé entre ChargeServiceImpl
 * (génération/aperçu des charges) et CoOwnerDashboardServiceImpl (KPI "Charge annuelle"),
 * pour garantir que les deux affichent toujours exactement le même montant.
 */
public final class ChargeAllocationUtil {

    private ChargeAllocationUtil() {
    }

    /**
     * Répartit un montant total entre plusieurs copropriétaires, proportionnellement à leur
     * tantième, en FCFA entiers — méthode du "plus grand reste" (largest remainder method).
     *
     * Contrairement à un arrondi indépendant part par part (qui peut faire disparaître un petit
     * tantième à 0 FCFA et faire perdre/gagner quelques FCFA sur le total), cette méthode garantit :
     * - la somme des parts retournées est TOUJOURS exactement égale à totalAmount
     * - aucun copropriétaire avec un tantième > 0 ne peut se retrouver à 0 FCFA tant que le
     *   reste à distribuer n'est pas épuisé avant que son tour n'arrive
     *
     * @param totalAmount       montant total à répartir
     * @param tantiemeByOwnerId tantième de chaque copropriétaire, par id
     * @return la part finale (en FCFA entiers) de chaque copropriétaire, par id
     */
    public static Map<Long, BigDecimal> distributeByLargestRemainder(BigDecimal totalAmount, Map<Long, BigDecimal> tantiemeByOwnerId) {

        // 1. Calcule la part exacte (non arrondie) de chacun, puis sa part arrondie vers le bas
        Map<Long, BigDecimal> exactShareByOwnerId = new LinkedHashMap<>();
        Map<Long, BigDecimal> flooredShareByOwnerId = new LinkedHashMap<>();
        BigDecimal sumFloored = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> entry : tantiemeByOwnerId.entrySet()) {
            BigDecimal exactShare = totalAmount.multiply(entry.getValue())
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            BigDecimal flooredShare = exactShare.setScale(0, RoundingMode.DOWN);

            exactShareByOwnerId.put(entry.getKey(), exactShare);
            flooredShareByOwnerId.put(entry.getKey(), flooredShare);
            sumFloored = sumFloored.add(flooredShare);
        }

        // 2. Nombre de FCFA restant à distribuer (au plus 1 par copropriétaire, un par un)
        int remainderCount = totalAmount.subtract(sumFloored)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        // 3. Classe les copropriétaires selon la partie décimale perdue à l'arrondi, la plus grande d'abord
        List<Long> orderedByLostRemainder = tantiemeByOwnerId.keySet().stream()
                .sorted((a, b) -> {
                    BigDecimal remainderA = exactShareByOwnerId.get(a).subtract(flooredShareByOwnerId.get(a));
                    BigDecimal remainderB = exactShareByOwnerId.get(b).subtract(flooredShareByOwnerId.get(b));
                    return remainderB.compareTo(remainderA);
                })
                .toList();

        // 4. Distribue le reste, 1 FCFA à la fois, à ceux qui ont le plus perdu à l'arrondi
        Map<Long, BigDecimal> finalShareByOwnerId = new LinkedHashMap<>(flooredShareByOwnerId);
        for (int i = 0; i < remainderCount && i < orderedByLostRemainder.size(); i++) {
            Long ownerId = orderedByLostRemainder.get(i);
            finalShareByOwnerId.put(ownerId, finalShareByOwnerId.get(ownerId).add(BigDecimal.ONE));
        }

        return finalShareByOwnerId;
    }
}