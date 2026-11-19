package com.example.solimus.services.specialty;

import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.entities.Specialty;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catalogue des spécialités (catégories de travaux : plomberie, menuiserie…), partagé par :
 * <ul>
 *   <li>l'inscription d'un prestataire, qui choisit SA spécialité (liste publique, sans connexion) ;</li>
 *   <li>la création d'une demande de travaux, qui précise la spécialité requise ;</li>
 *   <li>la diffusion : seuls les prestataires de la spécialité demandée reçoivent la demande.</li>
 * </ul>
 * Le CRUD (création, modification, suppression) est réservé au syndic (SyndicSettingsService).
 */
@Service
@RequiredArgsConstructor
public class SpecialtyCatalogService {

    private final SpecialtyRepository specialtyRepository;
    private final MinioService minioService;

    @Transactional(readOnly = true)
    public List<SpecialtyDTO> listAll(String search) {
        String term = search == null ? "" : search.trim();
        List<Specialty> specialties = term.isEmpty()
                ? specialtyRepository.findAllByOrderByNameAsc()
                : specialtyRepository.findByNameContainingIgnoreCaseOrderByNameAsc(term);
        return specialties.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SpecialtyDTO getById(Long id) {
        return specialtyRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité non trouvée"));
    }

    public SpecialtyDTO toDto(Specialty specialty) {
        return SpecialtyDTO.builder()
                .id(specialty.getId())
                .name(specialty.getName())
                .description(specialty.getDescription())
                .icon(specialty.getIcon())
                .iconUrl(iconUrl(specialty.getIcon()))
                .build();
    }

    // Icône téléversée = clé de fichier MinIO (avec extension) ; sinon simple nom d'icône locale ("plumbing")
    private String iconUrl(String icon) {
        if (icon == null || icon.isBlank() || !icon.contains(".")) {
            return null;
        }
        return minioService.getFileUrl(icon);
    }
}
