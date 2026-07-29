package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.*;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanChangeResponseDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicSubscriptionHistoryDTO;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PlanService {

    // ===== Formules Syndic =====
    SyndicPlanDTO createSyndicPlan(SyndicPlanRequestDTO dto);
    SyndicPlanDTO updateSyndicPlan(Long id, SyndicPlanRequestDTO dto);
    SyndicPlanDTO toggleSyndicPlanStatus(Long id, boolean active);
    void deleteSyndicPlan(Long id);

    // ===== Formules Prestataire =====
    ProviderPlanDTO createProviderPlan(ProviderPlanRequestDTO dto);
    ProviderPlanDTO updateProviderPlan(Long id, ProviderPlanRequestDTO dto);
    ProviderPlanDTO toggleProviderPlanStatus(Long id, boolean active);
    void deleteProviderPlan(Long id);

    // ===== Listing unifié + KPIs =====
    List<PlanOverviewDTO> getAllPlansOverview();
    SubscriptionKpiDTO getSubscriptionKpis();

    // ===== Liste unifiée des abonnés (Syndic + Prestataire) =====
    SubscriberListResponseDTO getAllSubscribers(String search, SubscriptionStatus status,
                                                 SubscriberType subscriberType, int page, int size);

    // ===== Détail d'un abonné précis, ouvert via l'icône œil de la liste =====
    SubscriberDetailDTO getSubscriberDetail(Long subscriptionId, SubscriberType subscriberType);

    // ===== Bloc "Détails du client" léger, affiché en haut des modales d'action (Suspendre, Réactiver...) =====
    SubscriberQuickInfoDTO getSubscriberQuickInfo(Long subscriptionId, SubscriberType subscriberType);

    // ===== Bloc "Détails du client" léger, affiché en haut de la modale "Renouveler l'abonnement" =====
    SubscriberRenewalInfoDTO getSubscriberRenewalInfo(Long subscriptionId, SubscriberType subscriberType);

    // ===== Options du formulaire de renouvellement (formules disponibles + durées) =====
    RenewalFormOptionsDTO getRenewalFormOptions(SubscriberType subscriberType);

    // ===== Historique des paiements d'un abonné précis (vu par l'admin, pas par le syndic lui-même) =====
    Page<SyndicSubscriptionHistoryDTO> getSubscriberPaymentHistory(Long subscriptionId, SubscriberType subscriberType,
                                                                     int page, int size);

    // ===== Suspension d'un compte abonné (bloque le login + désactive l'abonnement) =====
    void suspendSubscriber(Long subscriptionId, SubscriberType subscriberType, SuspendSubscriberDTO dto);

    // ===== Réactivation d'un compte abonné précédemment suspendu =====
    void reactivateSubscriber(Long subscriptionId, SubscriberType subscriberType, boolean notifyClient);

    // ===== Renouvellement manuel de l'abonnement d'un abonné par l'admin (passe quand même par TouchPay) =====
    SyndicPlanChangeResponseDTO renewSubscriber(Long subscriptionId, SubscriberType subscriberType,
                                                 AdminRenewSubscriptionDTO dto);
}