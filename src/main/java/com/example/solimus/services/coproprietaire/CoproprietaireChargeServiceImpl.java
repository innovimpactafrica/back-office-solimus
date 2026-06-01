package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.charge.ChargeAllocationDetailDTO;
import com.example.solimus.dtos.charge.ChargeAllocationSummaryDTO;
import com.example.solimus.dtos.charge.ChargeLineDTO;
import com.example.solimus.dtos.charge.MyChargesSummaryDTO;
import com.example.solimus.entities.ChargeAllocation;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ChargeAllocationRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoproprietaireChargeServiceImpl implements CoproprietaireChargeService {

    private final ChargeAllocationRepository allocationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public MyChargesSummaryDTO getMesCharges(Integer page, Integer size, ChargeStatus status, String search) {

        User currentOwner = getCurrentUser();

        // Valeurs par défaut pour pagination
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 10;
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        // Recherche paginée avec filtres
        Page<ChargeAllocation> allocationPage = allocationRepository
            .findByOwnerIdWithFilters(currentOwner.getId(), status, search, pageable);

        // Calculer les statistiques sur toutes les allocations (pas seulement la page)
        List<ChargeAllocation> allAllocations = allocationRepository
            .findAllByOwnerIdOrderByChargeDueDateAsc(currentOwner.getId());

        BigDecimal totalAPayer = allAllocations.stream()
            .filter(a -> a.getStatus() == ChargeStatus.EN_ATTENTE)
            .map(ChargeAllocation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int chargesEnAttente = (int) allAllocations.stream()
            .filter(a -> a.getStatus() == ChargeStatus.EN_ATTENTE)
            .count();

        LocalDate prochaineEcheance = allAllocations.stream()
            .filter(a -> a.getStatus() == ChargeStatus.EN_ATTENTE)
            .map(a -> a.getCharge().getDueDate())
            .filter(d -> d != null && !d.isBefore(LocalDate.now()))
            .min(LocalDate::compareTo)
            .orElse(null);

        return MyChargesSummaryDTO.builder()
            .totalAPayer(totalAPayer)
            .chargesEnAttente(chargesEnAttente)
            .prochaineEcheance(prochaineEcheance)
            .charges(allocationPage.getContent().stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList()))
            .totalPages(allocationPage.getTotalPages())
            .totalElements(allocationPage.getTotalElements())
            .currentPage(allocationPage.getNumber())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeAllocationDetailDTO getChargeDetail(Long allocationId) {

        User currentOwner = getCurrentUser();

        ChargeAllocation allocation = allocationRepository
            .findById(allocationId)
            .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));

        if (!allocation.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'avez pas accès à cette charge");
        }

        return toDetailDTO(allocation);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private ChargeAllocationSummaryDTO toSummaryDTO(ChargeAllocation a) {
        return ChargeAllocationSummaryDTO.builder()
            .idAllocation(a.getId())
            .reference(a.getReference())
            .title(a.getCharge().getTitle())
            .type(a.getCharge().getType())
            .amount(a.getAmount())
            .dueDate(a.getCharge().getDueDate())
            .status(a.getStatus())
            .residenceName(a.getCharge().getResidence().getName())
            .propertyReference(a.getProperty().getReference())
            .build();
    }

    private ChargeAllocationDetailDTO toDetailDTO(ChargeAllocation a) {
        List<ChargeLineDTO> lines = a.getCharge().getLines() != null
            ? a.getCharge().getLines().stream()
                .map(l -> ChargeLineDTO.builder()
                    .label(l.getLabel())
                    .amount(l.getAmount())
                    .build())
                .collect(Collectors.toList())
            : new ArrayList<>();

        return ChargeAllocationDetailDTO.builder()
            .idAllocation(a.getId())
            .reference(a.getReference())
            .title(a.getCharge().getTitle())
            .type(a.getCharge().getType())
            .amount(a.getAmount())
            .totalAmount(a.getCharge().getTotalAmount())
            .dueDate(a.getCharge().getDueDate())
            .status(a.getStatus())
            .period(a.getCharge().getPeriod())
            .residenceName(a.getCharge().getResidence().getName())
            .propertyReference(a.getProperty().getReference())
            .description(a.getCharge().getDescription())
            .lines(lines)
            .documentUrls(a.getCharge().getDocumentUrls())
            .createdAt(a.getCharge().getCreatedAt())
            .build();
    }
}
