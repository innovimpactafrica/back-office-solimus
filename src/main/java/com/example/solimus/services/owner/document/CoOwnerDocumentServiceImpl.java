package com.example.solimus.services.owner.document;

import com.example.solimus.dtos.owner.document.CoOwnerDocumentDTO;
import com.example.solimus.dtos.owner.document.CoOwnerDocumentListResponseDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.CoOwnerDocumentRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoOwnerDocumentServiceImpl implements CoOwnerDocumentService {

    private final CoOwnerDocumentRepository coOwnerDocumentRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // Liste unifiée des documents d'un copropriétaire (AG + charges exceptionnelles)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public CoOwnerDocumentListResponseDTO getMyDocuments(String search, String category, int page, int size) {

        // Récupère le copropriétaire actuellement connecté
        User currentUser = getCurrentUser();

        // Prépare la pagination
        Pageable pageable = PageRequest.of(page, size);

        // Nettoie le texte de recherche (retire les espaces inutiles, met null si vide)
        String searchFilter = null;
        if (search != null && !search.isBlank()) {
            searchFilter = search.trim();
        }

        // Nettoie le filtre de catégorie
        String categoryFilter = null;
        if (category != null && !category.isBlank()) {
            categoryFilter = category.trim();
        }

        // Récupère la page de résultats fusionnés (AG + charges exceptionnelles)
        Page<Object[]> resultPage = coOwnerDocumentRepository.searchCoOwnerDocuments(
                currentUser.getId(), searchFilter, categoryFilter, pageable);

        List<CoOwnerDocumentDTO> documents = new ArrayList<>();

        // Chaque ligne est un tableau brut de colonnes, il faut extraire chaque valeur une par une,
        // dans l'ordre exact du SELECT de la requête (source_type, source_id, file_name, file_url,
        // file_size_kb, category, created_at)
        for (Object[] row : resultPage.getContent()) {

            String sourceType = (String) row[0];
            Long sourceId = ((Number) row[1]).longValue();
            String fileName = (String) row[2];
            String fileUrl = (String) row[3];
            Long fileSizeKb = row[4] != null ? ((Number) row[4]).longValue() : null;
            String cat = (String) row[5];

            // La date revient sous forme de Timestamp SQL, à convertir en LocalDateTime
            Timestamp createdAtTimestamp = (Timestamp) row[6];
            LocalDateTime createdAt = createdAtTimestamp != null ? createdAtTimestamp.toLocalDateTime() : null;

            documents.add(CoOwnerDocumentDTO.builder()
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .fileName(fileName)
                    .fileUrl(fileUrl)
                    .fileSizeKb(fileSizeKb)
                    .category(cat)
                    .createdAt(createdAt)
                    .build());
        }

        // Construit la réponse finale : documents de la page + infos de pagination
        return CoOwnerDocumentListResponseDTO.builder()
                .totalCount(resultPage.getTotalElements())
                .documents(documents)
                .currentPage(resultPage.getNumber())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    // Récupère l'utilisateur actuellement connecté à partir du token de sécurité
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}