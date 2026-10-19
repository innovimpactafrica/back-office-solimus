package com.example.solimus.services.syndic.subscription;

import com.example.solimus.dtos.syndic.subscription.InitiateSyndicPlanChangeDTO;
import com.example.solimus.dtos.syndic.subscription.RenewSyndicSubscriptionDTO;
import com.example.solimus.dtos.syndic.subscription.MySyndicSubscriptionDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanChangeResponseDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanOptionDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicSubscriptionHistoryDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface SyndicSubscriptionService {

    /**
     * Retourne l'abonnement actuel du syndic connecté (formule, statut, dates, montant, fonctionnalités).
     */
    MySyndicSubscriptionDTO getMySubscription();

    /**
     * Retourne l'historique paginé des paiements d'abonnement du syndic connecté, du plus récent au plus ancien.
     */
    Page<SyndicSubscriptionHistoryDTO> getPaymentHistory(int page, int size);

    /**
     * Retourne les formules actives proposées au choix, pour la modale "Choisir une nouvelle formule".
     */
    List<SyndicPlanOptionDTO> listAvailablePlans();

    /**
     * Initie le paiement self-service d'un changement de formule : crée un abonnement PENDING
     * et retourne l'URL du pont de paiement TouchPay.
     */
    SyndicPlanChangeResponseDTO initiateChangePlan(InitiateSyndicPlanChangeDTO dto);

    /**
     * Initie le paiement self-service du renouvellement de la formule actuelle (même flux, même
     * réponse que le changement de formule) : abonnement PENDING prolongeant la période en cours,
     * activé uniquement au callback TouchPay confirmé.
     */
    SyndicPlanChangeResponseDTO initiateRenewal(RenewSyndicSubscriptionDTO dto);
}
