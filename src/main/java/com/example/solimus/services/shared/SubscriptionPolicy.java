package com.example.solimus.services.shared;

import com.example.solimus.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Abonnements (syndic et prestataire) : activés ou suspendus.
 * <p>
 * Suspendus par défaut ({@code app.subscriptions.enabled=false}) : l'application ne gère aucun paiement
 * d'abonnement. Tout compte, une fois créé et connecté, a accès à toutes les fonctionnalités :
 * <ul>
 *   <li>aucune vérification de formule ({@code PlanFeatureGuard}) ni de limite de résidences / lots
 *       ({@code PlanLimitGuard}) pour le syndic ;</li>
 *   <li>aucun blocage des routes {@code /api/provider/**} ({@code SubscriptionFilter}) ;</li>
 *   <li>prestataires notifiés des demandes de travaux sans condition d'abonnement ;</li>
 *   <li>création d'un syndic par l'admin sans formule, durée ni paiement ;</li>
 *   <li>endpoints de souscription / renouvellement / changement de formule refusés (400).</li>
 * </ul>
 * Les données d'abonnement existantes ne sont ni modifiées ni supprimées : {@code SUBSCRIPTIONS_ENABLED=true}
 * rétablit le comportement précédent sans migration.
 */
@Component
public class SubscriptionPolicy {

    private final boolean enabled;

    public SubscriptionPolicy(@Value("${app.subscriptions.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Pour les endpoints de souscription, renouvellement et changement de formule. */
    public void assertEnabled() {
        if (!enabled) {
            throw new BadRequestException("Les abonnements sont suspendus : l'accès à toutes les fonctionnalités "
                    + "est inclus, aucun paiement d'abonnement n'est nécessaire.");
        }
    }
}
