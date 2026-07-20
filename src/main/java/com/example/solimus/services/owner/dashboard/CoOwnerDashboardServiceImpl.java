package com.example.solimus.services.owner.dashboard;

import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationRowDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerDashboardHeaderDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerDashboardKpiDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerPendingChargeDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerPropertySelectorDTO;
import com.example.solimus.dtos.owner.meeting.OwnerMeetingCardDTO;
import com.example.solimus.entities.Budget;
import com.example.solimus.entities.ChargeCallItem;
import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.Notification;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoOwnerDashboardServiceImpl implements CoOwnerDashboardService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final NotificationRepository notificationRepository;
    private final ResidenceRepository residenceRepository;

    // =========================================================================
    // Liste des biens du copropriétaire (pour le sélecteur "Mon bien")
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<OwnerPropertySelectorDTO> getMyProperties() {

        // Récupère le copropriétaire actuellement connecté
        User currentUser = getCurrentUser();

        // Récupère tous les lots appartenant à ce copropriétaire
        List<Property> properties = propertyRepository.findAllByOwnerId(currentUser.getId());

        List<OwnerPropertySelectorDTO> dtos = new ArrayList<>();

        // Construit une ligne pour chaque bien trouvé
        for (Property property : properties) {
            dtos.add(OwnerPropertySelectorDTO.builder()
                    .propertyId(property.getId())
                    .residenceId(property.getResidence().getId())
                    .residenceName(property.getResidence().getName())
                    .propertyReference(property.getReference())
                    .build());
        }

        return dtos;
    }

    // =========================================================================
    // En-tête du dashboard (prénom, photo, compteur de notifications non lues)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public OwnerDashboardHeaderDTO getDashboardHeader() {

        User currentUser = getCurrentUser();

        long unreadCount = notificationRepository.countByUserAndReadFalse(currentUser);

        return OwnerDashboardHeaderDTO.builder()
                .firstName(currentUser.getFirstName())
                .photoUrl(currentUser.getProfilePhotoUrl())
                .unreadNotificationsCount(unreadCount)
                .build();
    }

    // =========================================================================
    // Liste paginée des notifications du copropriétaire connecté
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public NotificationListResponseDTO getMyNotifications(int page, int size) {

        User currentUser = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notificationPage = notificationRepository.findByUserOrderByCreatedAtDesc(currentUser, pageable);

        List<NotificationRowDTO> rows = new ArrayList<>();

        for (Notification notification : notificationPage.getContent()) {
            rows.add(NotificationRowDTO.builder()
                    .id(notification.getId())
                    .title(notification.getTitle())
                    .body(notification.getBody())
                    .read(notification.getRead())
                    .createdAt(notification.getCreatedAt())
                    .build());
        }

        return NotificationListResponseDTO.builder()
                .totalCount(notificationPage.getTotalElements())
                .notifications(rows)
                .currentPage(notificationPage.getNumber())
                .totalPages(notificationPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // Marque toutes les notifications du copropriétaire connecté comme lues
    // =========================================================================
    @Override
    @Transactional
    public void markAllNotificationsAsRead() {

        User currentUser = getCurrentUser();
        notificationRepository.markAllAsReadByUser(currentUser);
    }

    // =========================================================================
    // KPIs du dashboard (Charge annuel + Restant à payer), pour une résidence précise
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public OwnerDashboardKpiDTO getDashboardKpis(Long residenceId) {

        User currentUser = getCurrentUser();

        // Vérifie que ce copropriétaire a bien au moins un lot dans cette résidence
        boolean hasLot = propertyRepository.existsByOwnerIdAndResidenceId(currentUser.getId(), residenceId);
        if (!hasLot) {
            throw new ForbiddenException("Vous n'avez pas de lot dans cette résidence");
        }

        int currentYear = Year.now().getValue();

        // ===== 1. CHARGE ANNUELLE =====
        BigDecimal annualCharge = BigDecimal.ZERO;
        var budgetOpt = budgetRepository.findByResidenceIdAndAnnee(residenceId, currentYear);

        if (budgetOpt.isPresent()) {
            var budget = budgetOpt.get();

            // Somme des tantièmes de ce copropriétaire dans cette résidence (tous ses lots confondus)
            BigDecimal tantiemeCoOwner = BigDecimal.ZERO;
            List<Property> properties = propertyRepository.findAllByOwnerId(currentUser.getId());
            for (Property p : properties) {
                if (p.getResidence().getId().equals(residenceId)) {
                    tantiemeCoOwner = tantiemeCoOwner.add(p.getTantieme());
                }
            }

            annualCharge = budget.getBudgetTotal()
                    .multiply(tantiemeCoOwner)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }

        // ===== 2. RESTANT À PAYER =====
        // Somme des remainingAmount de tous les ChargeCallItem de ce copropriétaire, pour cette résidence
        BigDecimal remainingToPay = chargeCallItemRepository.sumRemainingAmountByCoOwnerAndResidence(
                currentUser.getId(), residenceId);

        return OwnerDashboardKpiDTO.builder()
                .annualCharge(annualCharge)
                .remainingToPay(remainingToPay)
                .build();
    }

    // =========================================================================
    // Charges en attente pour le dashboard (aperçu limité)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<OwnerPendingChargeDTO> getPendingCharges(Long residenceId) {

        User currentUser = getCurrentUser();

        Pageable pageable = PageRequest.of(0, 3);
        List<ChargeCallItem> items = chargeCallItemRepository.findPendingItemsByCoOwnerAndResidence(
                currentUser.getId(), residenceId, pageable);

        // Récupère TOUS les lots de ce copropriétaire dans cette résidence, une seule fois
        // (pas besoin de refaire la recherche à chaque item de la boucle)
        List<Property> allProperties = propertyRepository.findAllByOwnerId(currentUser.getId());
        List<String> propertyReferences = new ArrayList<>();
        for (Property p : allProperties) {
            if (p.getResidence().getId().equals(residenceId)) {
                propertyReferences.add(p.getReference());
            }
        }
        // Assemble toutes les références en un seul texte séparé par virgule (ex: "A12, B4")
        String propertyReferencesText = String.join(", ", propertyReferences);

        List<OwnerPendingChargeDTO> dtos = new ArrayList<>();

        for (ChargeCallItem item : items) {

            Residence residence = item.getChargeCall().getBudget().getResidence();

            dtos.add(OwnerPendingChargeDTO.builder()
                    .chargeCallItemId(item.getId())
                    .title(buildChargeTitle(item.getChargeCall().getFrequency()))
                    .residenceName(residence.getName())
                    .propertyReference(propertyReferencesText)
                    .dueDate(item.getChargeCall().getDueDate())
                    .remainingAmount(item.getRemainingAmount())
                    .status("En attente")
                    .build());
        }

        return dtos;
    }

    // Construit le titre affiché selon la fréquence de l'appel de charges
    private String buildChargeTitle(ChargeFrequency frequency) {
        return switch (frequency) {
            case MENSUEL -> "Charges mensuelles";
            case TRIMESTRIEL -> "Charges trimestrielles";
        };
    }

    // =========================================================================
    // Prochaines réunions pour le dashboard (aperçu limité, scopé à une résidence précise)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<OwnerMeetingCardDTO> getUpcomingMeetings(Long residenceId) {

        User currentUser = getCurrentUser();

        // Limite à 2 résultats pour l'aperçu du dashboard
        Pageable pageable = PageRequest.of(0, 2);
        List<Meeting> meetings = meetingRepository.findUpcomingMeetingsByParticipantUserIdAndResidenceId(
                currentUser.getId(), residenceId, pageable);

        List<OwnerMeetingCardDTO> cards = new ArrayList<>();

        for (Meeting meeting : meetings) {

            long participantsCount = meetingParticipantRepository.countByMeetingId(meeting.getId());
            long documentsCount = meeting.getDocuments().size();

            cards.add(OwnerMeetingCardDTO.builder()
                    .id(meeting.getId())
                    .title(meeting.getTitle())
                    .type(meeting.getType())
                    .typeLabel(meeting.getType().getLabel())
                    .status(meeting.getStatus())
                    .statusLabel(meeting.getStatus().getLabel())
                    .meetingDate(meeting.getMeetingDate())
                    .startTime(meeting.getStartTime())
                    .endTime(meeting.getEndTime())
                    .location(meeting.getLocation())
                    .participantsCount(participantsCount)
                    .documentsCount(documentsCount)
                    .build());
        }

        return cards;
    }

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    // Récupère l'utilisateur actuellement connecté à partir du token de sécurité
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

}
