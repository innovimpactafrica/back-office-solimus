package com.example.solimus.services.document;

import com.example.solimus.dtos.document.CoOwnerDocumentDTO;
import com.example.solimus.entities.Charge;
import com.example.solimus.entities.ChargeAllocation;
import com.example.solimus.entities.MeetingDocument;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ChargeAllocationRepository;
import com.example.solimus.repositories.MeetingDocumentRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoOwnerDocumentServiceImpl implements CoOwnerDocumentService {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final MeetingDocumentRepository meetingDocumentRepository;
    private final ChargeAllocationRepository chargeAllocationRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerDocumentDTO> getMesDocuments(String search, String documentType, String source, Pageable pageable) {
        User currentOwner = getCurrentUser();
        
        // Récupérer la propriété et la résidence du copropriétaire
        List<Property> properties = propertyRepository.findAllByOwnerId(currentOwner.getId());
        if (properties.isEmpty()) {
            throw new ResourceNotFoundException("Aucune propriété trouvée pour ce copropriétaire");
        }
        Property property = properties.get(0);
        
        Long residenceId = property.getResidence().getId();
        
        // Source 1 — MeetingDocument
        List<MeetingDocument> meetingDocs = meetingDocumentRepository.findAllByMeetingResidenceId(residenceId);
        List<CoOwnerDocumentDTO> meetingDTOs = meetingDocs.stream()
                .map(doc -> CoOwnerDocumentDTO.builder()
                        .id(doc.getId())
                        .fileName(doc.getFileName())
                        .fileUrl(doc.getFileUrl())
                        .fileSizeKb(doc.getFileSizeKb())
                        .documentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : "AUTRE")
                        .date(doc.getCreatedAt() != null ? doc.getCreatedAt().toLocalDate() : null)
                        .source("MEETING")
                        .sourceId(doc.getMeeting() != null ? doc.getMeeting().getId() : null)
                        .build())
                .collect(Collectors.toList());
        
        // Source 2 — ChargeAllocation
        List<ChargeAllocation> allocations = chargeAllocationRepository.findByOwnerOrderByCreatedAtDesc(currentOwner);
        List<CoOwnerDocumentDTO> chargeDTOs = new ArrayList<>();
        
        for (ChargeAllocation allocation : allocations) {
            Charge charge = allocation.getCharge();
            if (charge != null && charge.getDocumentUrls() != null) {
                for (String docUrl : charge.getDocumentUrls()) {
                    chargeDTOs.add(CoOwnerDocumentDTO.builder()
                            .id(null) // Pas d'ID pour les documents de charge (utilisent l'URL)
                            .fileName(extractFileNameFromUrl(docUrl))
                            .fileUrl(docUrl)
                            .fileSizeKb(null) // Taille non disponible
                            .documentType("Charges")
                            .date(charge.getDueDate() != null ? charge.getDueDate() : 
                                  (charge.getCreatedAt() != null ? charge.getCreatedAt().toLocalDate() : null))
                            .source("CHARGE")
                            .sourceId(charge.getId())
                            .build());
                }
            }
        }
        
        // Fusionner les deux listes
        List<CoOwnerDocumentDTO> allDocuments = new ArrayList<>();
        allDocuments.addAll(meetingDTOs);
        allDocuments.addAll(chargeDTOs);
        
        // Appliquer les filtres
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.toLowerCase();
            allDocuments = allDocuments.stream()
                    .filter(doc -> doc.getFileName() != null && 
                                  doc.getFileName().toLowerCase().contains(searchLower))
                    .collect(Collectors.toList());
        }
        
        if (documentType != null && !documentType.trim().isEmpty()) {
            allDocuments = allDocuments.stream()
                    .filter(doc -> documentType.equalsIgnoreCase(doc.getDocumentType()))
                    .collect(Collectors.toList());
        }
        
        if (source != null && !source.trim().isEmpty()) {
            allDocuments = allDocuments.stream()
                    .filter(doc -> source.equalsIgnoreCase(doc.getSource()))
                    .collect(Collectors.toList());
        }
        
        // Trier par date décroissante (nullsLast)
        allDocuments.sort(Comparator.comparing(
                CoOwnerDocumentDTO::getDate, 
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        
        // Pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allDocuments.size());
        
        List<CoOwnerDocumentDTO> pagedDocuments = allDocuments.subList(
                Math.min(start, allDocuments.size()), 
                Math.min(end, allDocuments.size())
        );
        
        return new PageImpl<>(pagedDocuments, pageable, allDocuments.size());
    }
    
    /**
     * Extrait le nom du fichier depuis une URL.
     * Exemple: "https://minio.example.com/bucket/file.pdf" → "file.pdf"
     */
    private String extractFileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "document";
        }
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < url.length() - 1) {
            return url.substring(lastSlashIndex + 1);
        }
        return url;
    }
    
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
