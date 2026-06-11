package com.example.solimus.services.dashboard;

import com.example.solimus.dtos.dashboard.ChargeAllocationSummaryDTO;
import com.example.solimus.dtos.dashboard.CoOwnerDashboardDTO;
import com.example.solimus.dtos.dashboard.CoOwnerPropertyDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;
import com.example.solimus.entities.ChargeAllocation;
import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ChargeAllocationRepository;
import com.example.solimus.repositories.MeetingDocumentRepository;
import com.example.solimus.repositories.MeetingRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoOwnerDashboardServiceImpl implements CoOwnerDashboardService {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final ChargeAllocationRepository chargeAllocationRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingDocumentRepository meetingDocumentRepository;

    @Override
    @Transactional(readOnly = true)
    public CoOwnerDashboardDTO getDashboard(Long propertyId) {
        User currentOwner = getCurrentUser();

        // 1. Récupérer tous les biens du copropriétaire
        List<Property> properties = propertyRepository.findAllByOwnerId(currentOwner.getId());
        if (properties.isEmpty()) {
            throw new ResourceNotFoundException("Aucun bien trouvé pour ce copropriétaire");
        }

        // 2. Déterminer le bien sélectionné
        Property selectedProperty;
        if (propertyId != null) {
            selectedProperty = properties.stream()
                    .filter(p -> p.getId().equals(propertyId))
                    .findFirst()
                    .orElse(properties.get(0));
        } else {
            selectedProperty = properties.get(0);
        }

        // 3. Mapper les biens pour le dropdown
        List<CoOwnerPropertyDTO> propertyDTOs = properties.stream()
                .map(p -> CoOwnerPropertyDTO.builder()
                        .id(p.getId())
                        .reference(p.getReference())
                        .residenceName(p.getResidence().getName())
                        .build())
                .collect(Collectors.toList());

        // 4. Charges EN_ATTENTE (max 2)
        List<ChargeAllocation> pendingCharges = chargeAllocationRepository
                .findTop2ByOwnerIdAndPropertyIdAndStatusOrderByChargeDueDateAsc(
                        currentOwner.getId(),
                        selectedProperty.getId(),
                        ChargeStatus.EN_ATTENTE
                );
        List<ChargeAllocationSummaryDTO> chargeSummaries = pendingCharges.stream()
                .map(this::toChargeSummaryDTO)
                .collect(Collectors.toList());

        // 5. Prochaines réunions (max 2)
        List<Meeting> upcomingMeetings = meetingRepository
                .findTop2ByResidenceIdAndStatusOrderByMeetingDateAsc(
                        selectedProperty.getResidence().getId(),
                        MeetingStatus.A_VENIR
                );
        List<MeetingSummaryDTO> meetingSummaries = upcomingMeetings.stream()
                .map(this::toMeetingSummaryDTO)
                .collect(Collectors.toList());

        // 6. Total documents
        int meetingDocCount = meetingDocumentRepository.countByMeetingResidenceId(
                selectedProperty.getResidence().getId()
        );
        int chargeDocCount = chargeAllocationRepository.countDocumentsByOwnerId(
                currentOwner.getId()
        );
        int totalDocuments = meetingDocCount + (chargeDocCount > 0 ? chargeDocCount : 0);

        // 7. Santé financière de la résidence
        java.math.BigDecimal soldeActuel = chargeAllocationRepository
                .sumByResidenceIdAndStatus(
                        selectedProperty.getResidence().getId(),
                        ChargeStatus.PAYEE
                );
        java.math.BigDecimal montantArrieres = chargeAllocationRepository
                .sumByResidenceIdAndStatus(
                        selectedProperty.getResidence().getId(),
                        ChargeStatus.EN_RETARD
                );

        // 8. Construire le DTO
        return CoOwnerDashboardDTO.builder()
                .firstName(currentOwner.getFirstName())
                .lastName(currentOwner.getLastName())
                .photoUrl(currentOwner.getProfilePhotoUrl())
                .properties(propertyDTOs)
                .selectedPropertyId(selectedProperty.getId())
                .totalDocuments(totalDocuments)
                .chargesEnAttente(chargeSummaries)
                .prochainesReunions(meetingSummaries)
                .soldeActuelResidence(soldeActuel != null ? soldeActuel : java.math.BigDecimal.ZERO)
                .montantArrieresResidence(montantArrieres != null ? montantArrieres : java.math.BigDecimal.ZERO)
                .build();
    }

    private MeetingSummaryDTO toMeetingSummaryDTO(Meeting meeting) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String timeRange = meeting.getMeetingDate() != null
                ? meeting.getMeetingDate().format(timeFormatter) + " - " + 
                  meeting.getMeetingDate().plusHours(2).format(timeFormatter)
                : "";

        return MeetingSummaryDTO.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .type(meeting.getType())
                .status(meeting.getStatus())
                .meetingDate(meeting.getMeetingDate())
                .location(meeting.getLocation())
                .participantCount(meeting.getParticipants() != null ? meeting.getParticipants().size() : 0)
                .documentCount(meeting.getDocuments() != null ? meeting.getDocuments().size() : 0)
                .residenceId(meeting.getResidence() != null ? meeting.getResidence().getId() : null)
                .build();
    }

    private ChargeAllocationSummaryDTO toChargeSummaryDTO(ChargeAllocation allocation) {
        return ChargeAllocationSummaryDTO.builder()
                .id(allocation.getId())
                .title(allocation.getCharge() != null ? allocation.getCharge().getTitle() : "Charge")
                .amount(allocation.getAmount())
                .dueDate(allocation.getCharge() != null ? allocation.getCharge().getDueDate() : null)
                .status(allocation.getStatus())
                .propertyId(allocation.getProperty() != null ? allocation.getProperty().getId() : null)
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
