package com.example.solimus.services.tenant.accueil;

import com.example.solimus.dtos.tenant.accueil.TenantDashboardDTO;
import com.example.solimus.dtos.tenant.accueil.TenantTravauxSummaryDTO;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.User;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.SignalementRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantAccueilServiceImpl implements TenantAccueilService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final SignalementRepository signalementRepository;
    private final InterventionRequestRepository interventionRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public TenantDashboardDTO getDashboard() {

        // Récupère le locataire connecté et son bien
        User currentTenant = getCurrentUser();
        Property property = propertyRepository.findByTenantId(currentTenant.getId())
                .orElseThrow(() -> new ForbiddenException("Aucun bien n'est assigné à ce compte locataire"));

        // Compte les signalements du locataire par statut
        int pending = signalementRepository.countByTenantIdAndStatus(currentTenant.getId(), SignalementStatus.PENDING);
        int inProgress = signalementRepository.countByTenantIdAndStatus(currentTenant.getId(), SignalementStatus.IN_PROGRESS);
        int resolved = signalementRepository.countByTenantIdAndStatus(currentTenant.getId(), SignalementStatus.RESOLVED);

        // Récupère les travaux en cours ou planifiés (limité aux 3 plus récents)
        Pageable limit = PageRequest.of(0, 3, Sort.by("createdAt").descending());
        List<InterventionRequest> travaux = interventionRequestRepository
                .findByTenantIdAndStatusIn(currentTenant.getId(),
                        List.of(InterventionStatus.PENDING, InterventionStatus.STARTED), limit);

        // Transforme chaque intervention en résumé pour l'affichage
        List<TenantTravauxSummaryDTO> travauxDtos = travaux.stream()
                .map(this::buildTravauxSummary)
                .toList();

        // Construit le dashboard complet
        return TenantDashboardDTO.builder()
                .firstName(currentTenant.getFirstName())
                .propertyReference(property.getReference())
                .residenceName(property.getResidence().getName())
                .residencePhotoUrl(property.getResidence().getPhotoUrl())
                .bailActif(true) // toujours vrai tant que ce compte a accès à ce dashboard
                .pendingReportsCount(pending)
                .inProgressReportsCount(inProgress)
                .resolvedReportsCount(resolved)
                .travauxEnCours(travauxDtos)
                .build();
    }

    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    // Mappe le statut vers le libellé affiché ("En cours" / "Planifié")
    private TenantTravauxSummaryDTO buildTravauxSummary(InterventionRequest request) {
        String label = request.getStatus() == InterventionStatus.STARTED ? "En cours" : "Planifié";
        return TenantTravauxSummaryDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .specialtyIcon(request.getSpecialty() != null ? request.getSpecialty().getIcon() : null)
                .statusLabel(label)
                .status(request.getStatus())
                .build();
    }
}
