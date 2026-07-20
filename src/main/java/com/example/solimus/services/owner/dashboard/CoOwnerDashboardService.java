package com.example.solimus.services.owner.dashboard;

import com.example.solimus.dtos.owner.dashboard.*;
import com.example.solimus.dtos.owner.meeting.OwnerMeetingCardDTO;

import java.util.List;

public interface CoOwnerDashboardService {

    // Liste des biens du copropriétaire connecté (sélecteur "Mon bien")
    List<OwnerPropertySelectorDTO> getMyProperties();

    // En-tête du dashboard (prénom, photo, compteur de notifications non lues)
    OwnerDashboardHeaderDTO getDashboardHeader();

    // Liste paginée des notifications du copropriétaire connecté
    NotificationListResponseDTO getMyNotifications(int page, int size);

    // Marque toutes les notifications du copropriétaire connecté comme lues
    void markAllNotificationsAsRead();

    // KPIs du dashboard (Charge annuel + Restant à payer), pour une résidence précise
    OwnerDashboardKpiDTO getDashboardKpis(Long residenceId);

    // Charges en attente pour le dashboard (aperçu limité)
    List<OwnerPendingChargeDTO> getPendingCharges(Long residenceId);

    // Prochaines réunions pour le dashboard (aperçu limité, scopé à une résidence précise)
    List<OwnerMeetingCardDTO> getUpcomingMeetings(Long residenceId);

}
