package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.provider.profile.QuoteLineDTO;
import com.example.solimus.dtos.syndic.travaux.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyndicTravauxDashboardServiceImpl implements SyndicTravauxDashboardService {

    private final UserRepository userRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final QuoteRepository quoteRepository;
    private final ProviderProfileRepository providerProfileRepository;

    // Statuts considérés comme "ouverts" (tout sauf clôturé/annulé)
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

        // Récupère le syndic actuellement connecté
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

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Construit la pagination, triée du plus récent au plus ancien
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Récupère les incidents du syndic, avec filtres optionnels appliqués
        var incidentPage = interventionRequestRepository.searchForSyndicTravaux(
                currentSyndic.getId(), search, status, residenceId, pageable);

        // Transforme chaque incident en carte
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
    // ONGLET 1 — VUE GÉNÉRALE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicTravauxDetailDTO getVueGenerale(Long id) {

        // Récupère l'intervention et vérifie qu'elle appartient bien au syndic connecté
        InterventionRequest request = getRequestForSyndic(id);

        // Récupérer les informations du prestataire si sélectionné
        Double countStar = null;
        String emailPrest = null;
        String phoneNumberPrest = null;
        String dureeEstimee = null;

        if (request.getSelectedProvider() != null) {
            ProviderProfile profile = providerProfileRepository.findByUser(request.getSelectedProvider())
                    .orElse(null);
            if (profile != null) {
                countStar = profile.getRating();
            }
            emailPrest = request.getSelectedProvider().getEmail();
            phoneNumberPrest = request.getSelectedProvider().getPhone();

            // Récupérer la durée estimée depuis le devis accepté
            List<Quote> acceptedQuotes = quoteRepository.findByInterventionRequestAndStatus(request, QuoteStatus.ACCEPTED);
            if (!acceptedQuotes.isEmpty() && acceptedQuotes.get(0).getEstimatedDelay() != null) {
                dureeEstimee = acceptedQuotes.get(0).getEstimatedDelay().getLabel();
            }
        }

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
                .prestataireName(request.getSelectedProvider() != null
                        ? request.getSelectedProvider().getFirstName() + " " + request.getSelectedProvider().getLastName() : null)
                .coutEstime(request.getTotalAmount())
                .dureeEstimee(dureeEstimee)
                .countStar(countStar)
                .emailPrest(emailPrest)
                .phoneNumberPrest(phoneNumberPrest)
                // Résumé financier
                .avanceVersee(request.getDepositAmount())
                .totalEngage(request.getTotalAmount())
                .totalPaye(calculateTotalPaye(request))
                .participants(buildParticipants(request))
                .photoUrls(request.getPhotoUrls())
                .build();
    }

    // Calcule le montant total déjà payé : si remainingAmount vaut 0, tout est payé ;
    // sinon, seul l'acompte a été versé jusqu'à présent
    private BigDecimal calculateTotalPaye(InterventionRequest request) {
        if (request.getRemainingAmount() != null && request.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            return request.getTotalAmount();
        }
        return request.getDepositAmount() != null ? request.getDepositAmount() : BigDecimal.ZERO;
    }

    // =========================================================================
    // ONGLET 2 — DEVIS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SyndicQuoteCardDTO> getQuotes(Long id) {

        InterventionRequest request = getRequestForSyndic(id);

        // Récupère tous les devis reçus pour cette intervention, triés du moins cher au plus cher
        List<Quote> quotes = quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request);

        return quotes.stream()
                .map(q -> SyndicQuoteCardDTO.builder()
                        .id(q.getId())
                        .reference(q.getReference())
                        .providerName(q.getProvider().getFirstName() + " " + q.getProvider().getLastName())
                        .createdAt(q.getCreatedAt())
                        .totalAmount(q.getTotalAmount())
                        .status(q.getStatus())
                        .isRetained(q.getStatus() == QuoteStatus.ACCEPTED)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SyndicQuoteDetailDTO getQuoteDetail(Long id, Long quoteId) {

        InterventionRequest request = getRequestForSyndic(id);

        // Récupère le devis, erreur si introuvable
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis introuvable"));

        // Vérifie que ce devis appartient bien à cette intervention
        if (!quote.getInterventionRequest().getId().equals(request.getId())) {
            throw new ResourceNotFoundException("Ce devis n'appartient pas à cette intervention");
        }

        // Récupère le profil du prestataire (note, spécialité, entreprise...)
        ProviderProfile profile = providerProfileRepository.findByUser(quote.getProvider())
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // Sépare les lignes du devis en Matériel / Main d'œuvre
        List<QuoteLineDTO> materiaux = new ArrayList<>();
        List<QuoteLineDTO> mainOeuvre = new ArrayList<>();
        for (QuoteItem item : quote.getItems()) {
            QuoteLineDTO line = QuoteLineDTO.builder()
                    .description(item.getDescription())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(item.getTotalPrice())
                    .build();
            if (item.getType() == QuoteItemType.MATERIAL) materiaux.add(line);
            else mainOeuvre.add(line);
        }

        return SyndicQuoteDetailDTO.builder()
                .id(quote.getId())
                .reference(quote.getReference())
                .providerName(quote.getProvider().getFirstName() + " " + quote.getProvider().getLastName())
                .createdAt(quote.getCreatedAt())
                .estimatedDelayLabel(quote.getEstimatedDelay() != null ? quote.getEstimatedDelay().getLabel() : null)
                .additionalComments(quote.getAdditionalComments())
                .mainOeuvre(mainOeuvre)
                .sousTotalMainOeuvre(quote.getLaborTotalAmount())
                .materiaux(materiaux)
                .sousTotalMateriaux(quote.getMaterialTotalAmount())
                .totalTTC(quote.getTotalAmount())
                .status(quote.getStatus())
                .participants(buildParticipants(request))
                .providerCompanyName(profile.getCompanyName())
                .providerSpecialty(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .providerRating(profile.getRating())
                .providerPhone(quote.getProvider().getPhone())
                .providerEmail(quote.getProvider().getEmail())
                .build();
    }

    // =========================================================================
    // ONGLET 3 — INTERVENTION (dates, rapport prestataire, photos avant/après)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicInterventionTabDTO getInterventionTab(Long id) {

        InterventionRequest request = getRequestForSyndic(id);

        // Récupère le dernier commentaire (rapport) laissé par le prestataire sélectionné
        String lastComment = request.getComments().stream()
                .filter(c -> c.getAuthor() != null && c.getAuthor().equals(request.getSelectedProvider()))
                .reduce((first, second) -> second) // garde le dernier de la liste
                .map(InterventionComment::getContent)
                .orElse(null);

        // Récupère la durée estimée depuis le devis accepté, s'il existe
        String dureeEstimee = quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request).stream()
                .filter(q -> q.getStatus() == QuoteStatus.ACCEPTED)
                .findFirst()
                .map(q -> q.getEstimatedDelay() != null ? q.getEstimatedDelay().getLabel() : null)
                .orElse(null);

        return SyndicInterventionTabDTO.builder()
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .dureeEstimee(dureeEstimee)
                .providerReport(lastComment)
                .photosBefore(request.getPhotoUrls())
                .photosAfter(request.getWorkPhotoUrls())
                .build();
    }

    // =========================================================================
    // ONGLET 4 — HISTORIQUE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SyndicHistoryItemDTO> getHistory(Long id) {

        InterventionRequest request = getRequestForSyndic(id);

        // Transforme chaque entrée de l'historique de statuts en ligne affichable
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
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    // Récupère l'utilisateur actuellement authentifié via le SecurityContext
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

    // Construit le libellé de position (appartement ou équipement commun)
    private String buildPositionLabel(InterventionRequest request) {
        if (request.getLocationType() == IncidentLocationType.APPARTEMENT) {
            return request.getProperty() != null ? "Appartement " + request.getProperty().getReference() : "";
        } else {
            return request.getCommonFacility() != null && request.getCommonFacility().getFacilityType() != null
                    ? request.getCommonFacility().getFacilityType().getName() : "";
        }
    }

    // Récupère le nom de la personne à l'origine de la demande (copropriétaire ou syndic)
    private String getDeclaredByName(InterventionRequest request) {
        if (request.getOwner() != null) {
            return request.getOwner().getFirstName() + " " + request.getOwner().getLastName();
        } else if (request.getSyndic() != null) {
            return request.getSyndic().getFirstName() + " " + request.getSyndic().getLastName();
        }
        return null;
    }

    // Construit la liste des participants selon la règle métier :
    // SYNDIC seul si créé par le syndic sans owner ; OWNER+SYNDIC si créé par un owner ;
    // +PRESTATAIRE si un prestataire est déjà sélectionné
    private List<ParticipantDTO> buildParticipants(InterventionRequest request) {
        List<ParticipantDTO> participants = new ArrayList<>();

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
            // Si initié par un owner, on affiche aussi le syndic de la résidence
            User syndic = request.getResidence().getSyndic();
            participants.add(ParticipantDTO.builder()
                    .role("Syndic")
                    .name(syndic.getFirstName() + " " + syndic.getLastName())
                    .photoUrl(syndic.getProfilePhotoUrl())
                    .build());
        }

        if (request.getSelectedProvider() != null) {
            participants.add(ParticipantDTO.builder()
                    .role("Prestataire")
                    .name(request.getSelectedProvider().getFirstName() + " " + request.getSelectedProvider().getLastName())
                    .photoUrl(request.getSelectedProvider().getProfilePhotoUrl())
                    .build());
        }

        return participants;
    }

    // Détermine le rôle de l'auteur d'une entrée d'historique (Prestataire, Copropriétaire, ou Gestionnaire par défaut)
    private String resolveActorRole(User actor, InterventionRequest request) {
        if (actor == null) return "Système";
        if (request.getSelectedProvider() != null && actor.getId().equals(request.getSelectedProvider().getId())) return "Prestataire";
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
                .selectedProviderName(request.getSelectedProvider() != null
                        ? request.getSelectedProvider().getFirstName() + " " + request.getSelectedProvider().getLastName() : null)
                .createdAt(request.getCreatedAt())
                .photoCount(request.getPhotoUrls() != null ? request.getPhotoUrls().size() : 0)
                .commentCount(request.getComments() != null ? request.getComments().size() : 0)
                .build();
    }
}