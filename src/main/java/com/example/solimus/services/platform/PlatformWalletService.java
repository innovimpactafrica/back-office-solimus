package com.example.solimus.services.platform;

import com.example.solimus.dtos.admin.wallet.PlatformWalletSummaryDTO;
import com.example.solimus.dtos.admin.wallet.PlatformWalletTransactionDTO;
import com.example.solimus.entities.PlatformWalletTransaction;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.repositories.PlatformWalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Consultation du wallet de la plateforme (admin). Le solde est la somme du grand livre, jamais stocké. */
@Service
@RequiredArgsConstructor
public class PlatformWalletService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlatformWalletTransactionRepository repository;

    @Transactional(readOnly = true)
    public PlatformWalletSummaryDTO getSummary() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return PlatformWalletSummaryDTO.builder()
                .balance(repository.sumAll())
                .totalThisMonth(repository.sumInPeriod(startOfMonth, LocalDateTime.now().plusSeconds(1)))
                .totalLastMonth(repository.sumInPeriod(startOfMonth.minusMonths(1), startOfMonth))
                .transactionCount(repository.count())
                .currency("FCFA")
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PlatformWalletTransactionDTO> getTransactions(LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("La date de début doit précéder la date de fin");
        }
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return repository.findInPeriod(start, end, pageable).map(PlatformWalletService::toDto);
    }

    private static PlatformWalletTransactionDTO toDto(PlatformWalletTransaction t) {
        return PlatformWalletTransactionDTO.builder()
                .id(t.getId())
                .category(t.getCategory())
                .categoryLabel(t.getCategory() != null ? t.getCategory().getLabel() : null)
                .source(t.getSource())
                .sourceLabel(t.getSource() != null ? t.getSource().getLabel() : null)
                .amount(t.getAmount())
                .grossAmount(t.getGrossAmount())
                .commissionRate(t.getCommissionRate())
                .label(t.getLabel())
                .paymentReference(t.getPaymentReference())
                .interventionId(t.getInterventionRequest() != null ? t.getInterventionRequest().getId() : null)
                .interventionTitle(t.getInterventionRequest() != null ? t.getInterventionRequest().getTitle() : null)
                .providerId(t.getProvider() != null ? t.getProvider().getId() : null)
                .providerName(fullName(t.getProvider()))
                .syndicId(t.getSyndic() != null ? t.getSyndic().getId() : null)
                .syndicName(fullName(t.getSyndic()))
                .transactionDate(t.getTransactionDate())
                .build();
    }

    private static String fullName(User user) {
        return user == null ? null : (user.getFirstName() + " " + user.getLastName()).trim();
    }
}
