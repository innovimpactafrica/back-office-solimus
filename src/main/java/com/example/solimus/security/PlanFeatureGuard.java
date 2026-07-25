package com.example.solimus.security;

import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Vérifie si le syndic connecté a accès à une fonctionnalité précise, selon les fonctionnalités incluses
 * dans sa formule d'abonnement active. Utilisé depuis @PreAuthorize
 */
@Component("planFeatureGuard")
@RequiredArgsConstructor
public class PlanFeatureGuard {

    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final UserRepository userRepository;

    public boolean hasFeature(String featureName) {

        // Récupère le syndic actuellement authentifié
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);
        if (currentUser == null) {
            return false; // S'il n'existe pas on refuse directement
        }

        //Sinon
        //Transformer le String en Enum
        SyndicPlanFeature feature = SyndicPlanFeature.valueOf(featureName);

        // Vérifie que le syndic possède un abonnement actif incluant la fonctionnalité demandée
        return syndicSubscriptionRepository.findFirstBySyndicIdOrderByEndDateDesc(currentUser.getId())

                // Vérifie que l'abonnement trouvé est actuellement actif
                .filter(SyndicSubscription::isCurrentlyActive)

                // Si l'abonnement est actif, vérifie que son plan contient bien la fonctionnalité demandée (feature)
                .map(subscription ->
                        subscription.getSyndicPlan()
                                .getFeatures()
                                .contains(feature))

                // Si aucun abonnement n'a été trouvé ou si une étape précédente n'a pas abouti, retourne false (accès refusé)
                .orElse(false);    }
}
