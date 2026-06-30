package com.example.solimus.services.provider.travaux;

import com.example.solimus.dtos.provider.request.WorkflowStepDTO;
import com.example.solimus.dtos.provider.travaux.ProviderTravauxDTO;
import com.example.solimus.dtos.provider.travaux.ProviderTravauxDetailDTO;
import com.example.solimus.dtos.provider.travaux.ProviderTravauxPageDTO;
import com.example.solimus.entities.InterventionComment;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Quote;
import com.example.solimus.entities.User;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.QuoteRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderTravauxServiceImpl implements ProviderTravauxService {

    private final UserRepository userRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final QuoteRepository quoteRepository;
    private final MinioService minioService;

    // =========================================================================
    // LISTER MES TRAVAUX (devis accepté → travaux)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ProviderTravauxPageDTO getMyWorks(String search, InterventionStatus status, int page, int size) {
        // 1. Identifier le prestataire connecté
        User currentProvider = getCurrentUser();

        // 2. Normaliser la recherche (null si vide)
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        // 3. Récupérer les travaux (demandes dont CE prestataire est le selectedProvider)
        Pageable pageable = PageRequest.of(page, size);
        Page<InterventionRequest> works = interventionRequestRepository
                .findBySelectedProviderWithFilters(currentProvider, normalizedSearch, status, pageable);

        // 4. Compter les travaux actuellement en cours (statut STARTED → "En cours")
        long pendingCount = interventionRequestRepository
                .countBySelectedProviderIdAndStatus(currentProvider.getId(), InterventionStatus.STARTED);

        // 5. Construire la réponse
        return ProviderTravauxPageDTO.builder()
                .pendingCount(pendingCount)
                .works(works.map(this::mapToTravauxDTO))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderTravauxDetailDTO getWorkDetails(Long id) {
        User currentProvider = getCurrentUser();

        // 1. Récupérer l'intervention si le prestataire est le selectedProvider
        InterventionRequest request = interventionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // 2. Sécurité : Seul le prestataire assigné peut voir les détails
        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à consulter cette intervention");
        }

        // 3. Mapper vers ProviderTravauxDetailDTO
        return mapToTravauxDetailDTO(request);
    }

    @Override
    @Transactional
    public void startIntervention(Long requestId) {
        User currentProvider = getCurrentUser();

        // 1. Récupération de la demande
        InterventionRequest request = interventionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'intervention introuvable"));

        // 2. Sécurité - Vérifier que c'est bien CE prestataire qui a remporté le devis
        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à démarrer cette intervention. Seul le prestataire sélectionné par le syndic peut le faire.");
        }

        // 3. Workflow - On ne peut démarrer que si le devis a été validé
        if (request.getStatus() != InterventionStatus.QUOTE_VALIDATED) {
            throw new BadRequestException("Action impossible : Les travaux ne peuvent être démarrés que si le statut est 'Dévis validé'. Statut actuel : " + request.getStatus());
        }

        // 4. Mise à jour du statut, enregistrement dans l'historique et date de démarrage
        request.addStatusHistory(InterventionStatus.STARTED, currentProvider);
        request.setStartedAt(LocalDateTime.now());

        // 5. Sauvegarde en base de données
        interventionRequestRepository.save(request);
    }

    @Override
    @Transactional
    public void finishIntervention(Long requestId, String commentaire, MultipartFile[] photos) {

        User currentProvider = getCurrentUser();

        //Récupération de la demande
        InterventionRequest request = interventionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        // Sécurité - Vérifier que c'est bien CE prestataire qui a remporté le devis
        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à terminer cette intervention.");
        }

        if (request.getStatus() != InterventionStatus.STARTED) {
            throw new BadRequestException("L'intervention n'est pas en cours.");
        }

        // Ajouter le commentaire si fourni
        if (commentaire != null && !commentaire.isBlank()) {
            InterventionComment comment = new InterventionComment();
            comment.setContent(commentaire);
            comment.setInterventionRequest(request);
            comment.setAuthor(currentProvider);
            comment.setCreatedAt(LocalDateTime.now());
            request.getComments().add(comment);
        }

        // Ajouter les photos si fournies
        if (photos != null && photos.length > 0) {
            for (MultipartFile photo : photos) {
                try {
                    String photoUrl = minioService.uploadFile(photo, "interventions");
                    if (photoUrl != null) {
                        request.getWorkPhotoUrls().add(photoUrl);
                    }
                } catch (Exception e) {
                    log.error("Erreur lors de l'upload de la photo des travaux", e);
                    throw new RuntimeException("Erreur lors de l'upload de la photo");
                }
            }
        }

        request.addStatusHistory(InterventionStatus.FINISHED, currentProvider);
        request.setFinishedAt(LocalDateTime.now());
        interventionRequestRepository.save(request);
    }


    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    // Récupère l'utilisateur (prestataire) actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    // Mappe une demande d'intervention vers une carte "Travaux"
    private ProviderTravauxDTO mapToTravauxDTO(InterventionRequest request) {
        return ProviderTravauxDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : "N/A")
                .status(request.getStatus())
                .statusLabel(request.getStatus() != null ? request.getStatus().getLabel() : null)
                .createdAt(request.getCreatedAt())
                .build();
    }

    private ProviderTravauxDetailDTO mapToTravauxDetailDTO(InterventionRequest request) {
        // Initialisation des contacts avec une valeur par défaut "N/A"
        String residentPhone = "N/A";
        String residentEmail = "N/A";

        // Récupération des informations du contact selon le mode de gestion
        User contact = request.getManagementMode() == InterventionManagementMode.SYNDIC
                ? request.getSyndic()
                : request.getOwner();
        if (contact != null) {
            residentPhone = contact.getPhone();
            residentEmail = contact.getEmail();
        }

        // Convertir les chemins photos en URLs signées MinIO
        List<String> photoUrls = toPresignedUrls(request.getPhotoUrls());

        return ProviderTravauxDetailDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .statusLabel(request.getStatus() != null ? request.getStatus().getLabel() : null)
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : "N/A")
                .contactPhone(residentPhone)
                .contactEmail(residentEmail)
                .photoUrls(photoUrls)
                .workflowSteps(buildWorkflow(request))
                .createdAt(request.getCreatedAt())
                .build();
    }

    private List<WorkflowStepDTO> buildWorkflow(InterventionRequest request) {
        List<WorkflowStepDTO> steps = new ArrayList<>();

        // Étape 1 : Demande reçue
        steps.add(WorkflowStepDTO.builder()
                .label("Demande reçue")
                .completed(true)
                .date(request.getCreatedAt())
                .build());

        // Étape 2 : Devis envoyé (si le prestataire a soumis un devis)
        User currentProvider = getCurrentUser();
        Optional<Quote> quoteOpt = quoteRepository.findByInterventionRequestAndProvider(request, currentProvider);
        steps.add(WorkflowStepDTO.builder()
                .label("Devis envoyé")
                .completed(quoteOpt.isPresent())
                .date(quoteOpt.map(Quote::getCreatedAt).orElse(null))
                .build());

        // Étape 3 : Devis accepté (si un prestataire a été sélectionné)
        steps.add(WorkflowStepDTO.builder()
                .label("Devis accepté")
                .completed(request.getSelectedProvider() != null)
                .date(request.getQuoteAcceptedAt())
                .build());

        // Étape 4 : Travaux commencés
        steps.add(WorkflowStepDTO.builder()
                .label("Travaux commencés")
                .completed(request.getStartedAt() != null)
                .date(request.getStartedAt())
                .build());

        // Étape 5 : Travaux terminés
        steps.add(WorkflowStepDTO.builder()
                .label("Travaux terminés")
                .completed(request.getFinishedAt() != null)
                .date(request.getFinishedAt())
                .build());

        // Étape 6 : Validation finale
        steps.add(WorkflowStepDTO.builder()
                .label("Validation finale")
                .completed(request.getValidatedAt() != null)
                .date(request.getValidatedAt())
                .build());

        return steps;
    }

    private List<String> toPresignedUrls(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return minioService.toPresignedUrls(paths);
    }

}
