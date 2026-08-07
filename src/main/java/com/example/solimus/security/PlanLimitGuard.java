package com.example.solimus.security;

import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bloque la création de nouvelles ressources (résidences, lots/appartements) une fois la limite
 * numérique de la formule active du syndic atteinte (maxResidences / maxApartments — null = illimité).
 * Ne supprime ni ne bloque jamais ce qui existe déjà : en cas de rétrogradation, les ressources déjà
 * créées restent intactes, seule la création de NOUVELLES ressources est interdite tant que le
 * syndic reste au-dessus de la limite de sa nouvelle formule.
 */
@Component
@RequiredArgsConstructor
public class PlanLimitGuard {

    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;

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
            throw new ForbiddenException(
                    "Limite de résidences de votre formule atteinte (" + plan.getMaxResidences() +
                            "). Passez à une formule supérieure pour en ajouter davantage.");
        }
    }

    // Vérifie AVANT d'ajouter un ou plusieurs lots à une résidence — appeler avec le nombre de
    // lots sur le point d'être ajoutés (pas un par un), pour bloquer d'un coup un ajout groupé
    // qui dépasserait la limite, plutôt que d'en laisser passer une partie
    public void assertCanAddApartments(User syndic, int countToAdd) {

        // Récupère la formule d'abonnement actuellement active du syndic
        SyndicPlan plan = getActivePlan(syndic);

        // Si aucune limite n'est définie, le syndic peut ajouter autant de lots qu'il le souhaite
        if (plan.getMaxApartments() == null) {
            return;
        }

        // Sinon, compte le nombre de lots déjà créés, toutes résidences confondues
        long current = propertyRepository.countByResidenceSyndicId(syndic.getId());

        // Si l'ajout dépasserait la limite de la formule, on bloque
        if (current + countToAdd > plan.getMaxApartments()) {
            throw new ForbiddenException(
                    "Limite d'appartements de votre formule atteinte (" + plan.getMaxApartments() +
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
