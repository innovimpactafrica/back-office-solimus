package com.example.solimus.security;

import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.SyndicCoOwnerRelationRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bloque la création de nouvelles ressources (résidences, copropriétaires) une fois la limite
 * numérique de la formule active du syndic atteinte (maxResidences / maxCoOwners — null = illimité).
 * Ne supprime ni ne bloque jamais ce qui existe déjà : en cas de rétrogradation, les ressources déjà
 * créées restent intactes, seule la création de NOUVELLES ressources est interdite tant que le
 * syndic reste au-dessus de la limite de sa nouvelle formule.
 */
@Component
@RequiredArgsConstructor
public class PlanLimitGuard {

    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ResidenceRepository residenceRepository;
    private final SyndicCoOwnerRelationRepository syndicCoOwnerRelationRepository;

    public void assertCanAddResidence(User syndic) {

        // Récupère la formule d'abonnement actuellement active du syndic
        SyndicPlan plan = getActivePlan(syndic);

        // Si aucune limite n'est définie, le syndic peut ajouter autant de résidences qu'il le souhaite
        if (plan.getMaxResidences() == null) {
            return;
        }

        // Sinon, Compte le nombre de résidences déjà créées par ce syndic
        long current = residenceRepository.countBySyndicId(syndic.getId());

        // Si la limite de la formule est atteinte, on bloque l'ajout
        if (current >= plan.getMaxResidences()) {
            throw new BadRequestException(
                    "Limite de résidences de votre formule atteinte (" + plan.getMaxResidences() +
                            "). Passez à une formule supérieure pour en ajouter davantage.");
        }
    }

    public void assertCanAddCoOwner(User syndic) {

        // Récupère la formule d'abonnement actuellement active du syndic
        SyndicPlan plan = getActivePlan(syndic);

        // Si aucune limite n'est définie, le syndic peut ajouter autant de copropriétaires qu'il le souhaite
        if (plan.getMaxCoOwners() == null) {
            return;
        }

        // Sinon , Compte le nombre de copropriétaires déjà rattachés à ce syndic
        long current = syndicCoOwnerRelationRepository.countBySyndicId(syndic.getId());

        // Si la limite de la formule est atteinte, on bloque l'ajout
        if (current >= plan.getMaxCoOwners()) {
            throw new BadRequestException(
                    "Limite de copropriétaires de votre formule atteinte (" + plan.getMaxCoOwners() +
                            "). Passez à une formule supérieure pour en ajouter davantage.");
        }
    }

    // Récupère la formule de l'abonnement actif du syndic.
    // Si aucun abonnement actif n'est trouvé, une exception est levée.
    private SyndicPlan getActivePlan(User syndic) {

        // Cherche directement l'abonnement au statut ACTIVE — jamais "le plus récent par date de
        // fin", qui peut ramener un abonnement annulé dont la date de fin est simplement plus lointaine
        return syndicSubscriptionRepository.findFirstBySyndicIdAndStatus(syndic.getId(), SubscriptionStatus.ACTIVE)

                // Sécurité supplémentaire : le job d'expiration horaire n'a peut-être pas encore
                // basculé un abonnement dont la date de fin est déjà dépassée
                .filter(SyndicSubscription::isCurrentlyActive)

                // Retourne la formule liée à cet abonnement
                .map(SyndicSubscription::getSyndicPlan)

                // Si aucun abonnement actif n'est trouvé, lève une exception
                .orElseThrow(() ->
                        new BadRequestException("Aucun abonnement actif trouvé pour votre compte."));
    }
}
