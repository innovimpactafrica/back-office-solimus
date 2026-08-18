package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.travaux.ParticipantDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicHistoryItemDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxCardDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxClosureDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxDetailDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxListResponse;
import com.example.solimus.dtos.syndic.travaux.TravauxDashboardDTO;
import com.example.solimus.entities.InterventionComment;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.User;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyndicTravauxManualServiceImpl implements SyndicTravauxManualService {

    private final UserRepository userRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final MinioService minioService;

    // Statuts considérés comme "ouverts" (tout sauf clôturé/annulé) — même définition que le dashboard existant
    private static final List<InterventionStatus> OPEN_STATUSES = List.of(
            InterventionStatus.PENDING, InterventionStatus.SYNDIC_ASSIGNED,
            InterventionStatus.QUOTE_VALIDATED, InterventionStatus.STARTED, InterventionStatus.FINISHED
    );

    // =========================================================================
    // DASHBOARD (6 KPIs)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public TravauxDashboardDTO getDashboard() {
        User currentSyndic = getCurrentUser();

        return TravauxDashboardDTO.builder()
                .ouverts(interventionRequestRepository.countByResidenceSyndicIdAndStatusIn(currentSyndic.getId(), OPEN_STATUSES))
                .urgents(interventionRequestRepository.countByResidenceSyndicIdAndStatusInAndUrgencyLevel(
                        currentSyndic.getId(), OPEN_STATUSES, UrgencyLevel.URGENT))
                .enAttenteDevis(interventionRequestRepository.countByResidenceSyndicIdAndStatus(currentSyndic.getId(), InterventionStatus.PENDING))
                .enCours(interventionRequestRepository.countByResidenceSyndicIdAndStatus(currentSyndic.getId(), InterventionStatus.STARTED))
                .resolus(interventionRequestRepository.countByResidenceSyndicIdAndStatus(currentSyndic.getId(), InterventionStatus.FINISHED))
                .clotures(interventionRequestRepository.countByResidenceSyndicIdAndStatus(currentSyndic.getId(), InterventionStatus.FINAL_VALIDATION))
                .build();
    }

    // =========================================================================
    // LISTER LES INCIDENTS (paginé, avec recherche et filtres)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicTravauxListResponse getIncidents(String search, InterventionStatus status, Long residenceId, int page, int size) {
        User currentSyndic = getCurrentUser();

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        var incidentPage = interventionRequestRepository.searchForSyndicTravaux(
                currentSyndic.getId(), search, status, residenceId, pageable);

        List<SyndicTravauxCardDTO> cards = incidentPage.getContent().stream()
                .map(this::buildTravauxCard)
                .toList();

        return SyndicTravauxListResponse.builder()
                .incidents(cards)
                .currentPage(page)
                .totalPages(incidentPage.getTotalPages())
                .totalElements(incidentPage.getTotalElements())
                .build();
    }

    // =========================================================================
    // VUE GÉNÉRALE (sans les champs prestataire)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicTravauxDetailDTO getVueGenerale(Long id) {
        InterventionRequest request = getRequestForSyndic(id);

        List<String> photoUrls = request.getPhotoUrls() != null
                ? request.getPhotoUrls()
                : new ArrayList<>();

        return SyndicTravauxDetailDTO.builder()
                .id(request.getId())
                .reference(request.getReference())
                .title(request.getTitle())
                .description(request.getDescription())
                .urgencyLevel(request.getUrgencyLevel())
                .status(request.getStatus())
                .statusLabel(request.getStatus().getLabel())
                .residenceName(request.getResidence().getName())
                .positionLabel(buildPositionLabel(request))
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .declaredByName(getDeclaredByName(request))
                .createdAt(request.getCreatedAt())
                // Pas de circuit prestataire dans le flux manuel : ces champs restent vides
                .prestataireName(null)
                .coutEstime(null)
                .dureeEstimee(null)
                .countStar(null)
                .emailPrest(null)
                .phoneNumberPrest(null)
                .avanceVersee(null)
                .totalEngage(null)
                .totalPaye(null)
                .participants(buildParticipants(request))
                .photoUrls(photoUrls)
                .build();
    }

    // =========================================================================
    // HISTORIQUE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SyndicHistoryItemDTO> getHistory(Long id) {
        InterventionRequest request = getRequestForSyndic(id);

        return request.getHistory().stream()
                .map(h -> SyndicHistoryItemDTO.builder()
                        .actorName(h.getChangedBy() != null
                                ? h.getChangedBy().getFirstName() + " " + h.getChangedBy().getLastName() : "Système")
                        .actorRole(resolveActorRole(h.getChangedBy(), request))
                        .label(h.getStatus().getLabel())
                        .date(h.getCreatedAt())
                        .build())
                .toList();
    }

    // =========================================================================
    // ACTIONS MANUELLES DE PROGRESSION
    // =========================================================================

    @Override
    @Transactional
    public void markAsInProgress(Long id, String note) {
        InterventionRequest request = getRequestForSyndic(id);
        User currentSyndic = getCurrentUser();

        if (request.getStatus() != InterventionStatus.PENDING) {
            throw new BadRequestException("Cette demande n'est pas en attente");
        }

        request.addStatusHistory(InterventionStatus.STARTED, currentSyndic);
        request.setStartedAt(LocalDateTime.now());
        addNoteIfPresent(request, currentSyndic, note);
        interventionRequestRepository.save(request);

        notifyRequesters(request, "Votre demande est prise en charge par le syndic");
    }

    @Override
    @Transactional
    public void markAsFinished(Long id, String note) {
        InterventionRequest request = getRequestForSyndic(id);
        User currentSyndic = getCurrentUser();

        if (request.getStatus() != InterventionStatus.STARTED) {
            throw new BadRequestException("Cette demande n'est pas en cours");
        }

        request.addStatusHistory(InterventionStatus.FINISHED, currentSyndic);
        request.setFinishedAt(LocalDateTime.now());
        addNoteIfPresent(request, currentSyndic, note);
        interventionRequestRepository.save(request);

        notifyRequesters(request, "Les travaux de votre demande sont terminés");
    }

    @Override
    @Transactional
    public void closeIntervention(Long id, String closingNote, List<MultipartFile> photos) {
        InterventionRequest request = getRequestForSyndic(id);
        User currentSyndic = getCurrentUser();

        if (request.getStatus() != InterventionStatus.FINISHED) {
            throw new BadRequestException("Cette demande n'est pas encore terminée");
        }

        // Au moins une photo après travaux est obligatoire pour clôturer définitivement
        if (photos == null || photos.isEmpty()) {
            throw new BadRequestException("Au moins une photo après travaux est requise pour clôturer");
        }

        for (MultipartFile photo : photos) {
            try {
                String photoUrl = minioService.uploadFile(photo, "interventions");
                if (photoUrl != null) {
                    request.getWorkPhotoUrls().add(photoUrl);
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'upload d'une photo après travaux", e);
                throw new RuntimeException("Erreur lors de l'upload d'une photo");
            }
        }

        request.addStatusHistory(InterventionStatus.FINAL_VALIDATION, currentSyndic);
        request.setValidatedAt(LocalDateTime.now());
        request.setClosingNote(closingNote);
        addNoteIfPresent(request, currentSyndic, closingNote);
        interventionRequestRepository.save(request);

        notifyRequesters(request, "Votre demande a été clôturée par le syndic");
    }

    // =========================================================================
    // DÉTAIL DE CLÔTURE (dates clés, note, photos avant/après)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicTravauxClosureDTO getClosureDetail(Long id) {
        InterventionRequest request = getRequestForSyndic(id);

        return SyndicTravauxClosureDTO.builder()
                .startedAt(request.getStartedAt())
                .validatedAt(request.getValidatedAt())
                .closingNote(request.getClosingNote())
                .photosBefore(request.getPhotoUrls() != null ? request.getPhotoUrls() : new ArrayList<>())
                .photosAfter(request.getWorkPhotoUrls() != null ? request.getWorkPhotoUrls() : new ArrayList<>())
                .build();
    }

    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    // Récupère l'intervention et vérifie qu'elle appartient bien au syndic connecté
    private InterventionRequest getRequestForSyndic(Long id) {
        User currentSyndic = getCurrentUser();
        InterventionRequest request = interventionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette intervention");
        }
        return request;
    }

    // Ajoute un commentaire libre à l'intervention si une note a été fournie — réutilise
    // InterventionComment (existant) au lieu d'ajouter un champ "note" à l'historique de statut
    private void addNoteIfPresent(InterventionRequest request, User author, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        InterventionComment comment = new InterventionComment();
        comment.setContent(note);
        comment.setAuthor(author);
        comment.setInterventionRequest(request);
        request.getComments().add(comment);
    }

    // Notifie le(s) demandeur(s) — copropriétaire et/ou locataire, selon qui est renseigné —
    // en push (si activé) + email (toujours), à chaque changement de statut du flux manuel
    private void notifyRequesters(InterventionRequest request, String message) {
        String title = "Mise à jour de votre demande";
        if (request.getOwner() != null) {
            notifyUser(request.getOwner(), title, message);
        }
        if (request.getTenant() != null) {
            notifyUser(request.getTenant(), title, message);
        }
    }

    // Envoie une notification push (si activée) + un email (toujours) à un utilisateur donné,
    // sans jamais bloquer l'appelant en cas d'échec d'envoi
    private void notifyUser(User user, String title, String message) {
        try {
            if (user.isNotificationsEnabled()) {
                notificationService.sendPush(user.getId(), title, message);
            }
            emailService.sendEmail(user.getEmail(), title, message);
        } catch (Exception e) {
            System.err.println("Erreur envoi notification travaux à " + user.getEmail() + ": " + e.getMessage());
        }
    }

    // Construit le libellé de position (appartement ou équipement commun)
    private String buildPositionLabel(InterventionRequest request) {
        if (request.getLocationType() == IncidentLocationType.APPARTEMENT) {
            return request.getProperty() != null ? "Appartement " + request.getProperty().getReference() : "";
        } else {
            return request.getCommonFacility() != null && request.getCommonFacility().getFacilityType() != null
                    ? request.getCommonFacility().getFacilityType().getName() : "";
        }
    }

    // Récupère le nom de la personne à l'origine de la demande (locataire, copropriétaire ou syndic)
    private String getDeclaredByName(InterventionRequest request) {
        if (request.getTenant() != null) {
            return request.getTenant().getFirstName() + " " + request.getTenant().getLastName();
        } else if (request.getOwner() != null) {
            return request.getOwner().getFirstName() + " " + request.getOwner().getLastName();
        } else if (request.getSyndic() != null) {
            return request.getSyndic().getFirstName() + " " + request.getSyndic().getLastName();
        }
        return null;
    }

    // Construit la liste des participants : Locataire (si présent) + Copropriétaire (titulaire du
    // bien) + Syndic — pas de bloc Prestataire dans cette version manuelle
    private List<ParticipantDTO> buildParticipants(InterventionRequest request) {
        List<ParticipantDTO> participants = new ArrayList<>();

        if (request.getTenant() != null) {
            participants.add(ParticipantDTO.builder()
                    .role("Locataire")
                    .name(request.getTenant().getFirstName() + " " + request.getTenant().getLastName())
                    .photoUrl(request.getTenant().getProfilePhotoUrl())
                    .build());
        }

        if (request.getOwner() != null) {
            participants.add(ParticipantDTO.builder()
                    .role("Copropriétaire")
                    .name(request.getOwner().getFirstName() + " " + request.getOwner().getLastName())
                    .photoUrl(request.getOwner().getProfilePhotoUrl())
                    .build());
        }

        if (request.getSyndic() != null) {
            participants.add(ParticipantDTO.builder()
                    .role("Syndic")
                    .name(request.getSyndic().getFirstName() + " " + request.getSyndic().getLastName())
                    .photoUrl(request.getSyndic().getProfilePhotoUrl())
                    .build());
        } else if (request.getResidence().getSyndic() != null) {
            User syndic = request.getResidence().getSyndic();
            participants.add(ParticipantDTO.builder()
                    .role("Syndic")
                    .name(syndic.getFirstName() + " " + syndic.getLastName())
                    .photoUrl(syndic.getProfilePhotoUrl())
                    .build());
        }

        return participants;
    }

    // Détermine le rôle de l'auteur d'une entrée d'historique
    private String resolveActorRole(User actor, InterventionRequest request) {
        if (actor == null) return "Système";
        if (request.getTenant() != null && actor.getId().equals(request.getTenant().getId())) return "Locataire";
        if (request.getOwner() != null && actor.getId().equals(request.getOwner().getId())) return "Copropriétaire";
        return "Gestionnaire";
    }

    // Construit une carte pour la liste des incidents
    private SyndicTravauxCardDTO buildTravauxCard(InterventionRequest request) {
        return SyndicTravauxCardDTO.builder()
                .id(request.getId())
                .reference(request.getReference())
                .title(request.getTitle())
                .description(request.getDescription())
                .urgencyLevel(request.getUrgencyLevel())
                .status(request.getStatus())
                .statusLabel(request.getStatus().getLabel())
                .residenceName(request.getResidence().getName())
                .positionLabel(buildPositionLabel(request))
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .selectedProviderName(null) // pas de prestataire dans le flux manuel
                .createdAt(request.getCreatedAt())
                .photoCount(request.getPhotoUrls() != null ? request.getPhotoUrls().size() : 0)
                .commentCount(request.getComments() != null ? request.getComments().size() : 0)
                .build();
    }
}
