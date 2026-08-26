package com.example.solimus.services.provider.wallet;

import com.example.solimus.dtos.provider.wallet.RequestWithdrawalDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;
import com.example.solimus.dtos.provider.wallet.WalletTransactionDTO;
import com.example.solimus.entities.User;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.ProviderWithdrawalRequest;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.enums.TransactionType;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.ProviderWalletTransactionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.repositories.ProviderWalletRepository;
import com.example.solimus.repositories.WithdrawalRequestRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final ProviderWalletRepository walletRepository;
    private final ProviderWalletTransactionRepository walletTransactionRepository;
    private final WalletBalanceService walletBalanceService;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // =========================================================================
    // Récupération Wallet
    // =========================================================================
    @Override
    @Transactional
    public WalletDTO getMyWallet(int page, int size) {

        // 1. Récupérer l'utilisateur connecté (prestataire)
        User currentProvider = getCurrentUser();

        // 2. Récupérer le wallet ou en créer un si inexistant (plus de soldes à initialiser : ils
        // sont désormais toujours recalculés à la volée, jamais stockés)
        walletRepository.findByProviderId(currentProvider.getId())
                .orElseGet(() -> walletRepository.save(
                        ProviderWallet.builder().provider(currentProvider).build()));

        // 3. Récupérer les transactions avec pagination
        Page<WalletTransactionDTO> transactions = getTransactions(currentProvider.getId(), page, size);

        // 4. Construire et retourner le DTO — soldes recalculés à la volée (jamais stockés)
        return WalletDTO.builder()
                .availableBalance(walletBalanceService.getCurrentBalance(currentProvider.getId()))
                .pendingBalance(walletBalanceService.getPendingBalance(currentProvider.getId()))
                .totalThisMonth(walletBalanceService.getTotalThisMonth(currentProvider.getId()))
                .transactions(transactions)
                .build();
    }

    // =========================================================================
    // Crédit Wallet
    // =========================================================================

    @Override
    @Transactional
    public void creditWallet(Long providerId, BigDecimal amount) {

        // Recherche du wallet du prestataire ou création automatique s'il n'existe pas
        ProviderWallet wallet = walletRepository.findByProviderId(providerId)
                .orElseGet(() -> walletRepository.save(createWallet(providerId)));

        // Enregistre le crédit dans le grand livre — le solde disponible et le "total ce mois" sont
        // désormais toujours recalculés à la volée à partir de ces lignes, plus besoin de gérer
        // manuellement un changement de mois ici
        ProviderWalletTransaction transaction = new ProviderWalletTransaction();
        transaction.setWallet(wallet);
        transaction.setCategory(ProviderWalletTransactionCategory.INTERVENTION_PAYMENT);
        transaction.setAmount(amount);
        transaction.setLabel("Paiement intervention");
        transaction.setTransactionDate(LocalDateTime.now());
        walletTransactionRepository.save(transaction);
    }

    // =========================================================================
    // Demande de versement (retrait)
    // =========================================================================

    @Override
    @Transactional
    public WithdrawalRequestDTO requestWithdrawal(RequestWithdrawalDTO dto) {
        User currentProvider = getCurrentUser();

        // Récupérer le wallet du prestataire, ou le créer s'il n'existe pas encore (même logique
        // que getMyWallet/creditWallet)
        walletRepository.findByProviderId(currentProvider.getId())
                .orElseGet(() -> walletRepository.save(createWallet(currentProvider.getId())));

        // Pas de contrôle de solde ici — logique unifiée avec le syndic (voir
        // SyndicWalletServiceImpl.requestWithdrawal) : rien n'empêche de créer plusieurs demandes
        // PENDING même si leur somme dépasse le solde réel. Le contrôle anti-abus se fait au moment
        // de la validation admin (voir WithdrawalRequestServiceImpl.validateWithdrawalRequest), pas ici.

        // Créer la demande de versement (retrait)
        ProviderWithdrawalRequest retrait = ProviderWithdrawalRequest.builder()
                .reference(generateReference("WIT"))                     // Référence unique (ex: WIT-987654)
                .provider(currentProvider)                              // Prestataire effectuant la demande
                .amount(dto.getAmount())                                // Montant du retrait
                .method(dto.getMethod())                                 // Moyen de retrait (WAVE, ORANGE_MONEY)
                .phoneNumber(dto.getPhoneNumber())                       // Numéro de téléphone destinataire
                .status(WithdrawalStatus.PENDING)                        // Nouveau retrait toujours PENDING
                .build();

        withdrawalRequestRepository.save(retrait);

        // 3. Notifier le prestataire (push + email) si notifications activées
        if (currentProvider.isNotificationsEnabled()) {
            // Notification push
            notificationService.sendPush(
                    currentProvider.getId(),
                    "Demande de retrait reçue",
                    "Votre demande de retrait de " + dto.getAmount() + " FCFA a été enregistrée et est en attente de traitement."
            );

            // Notification email
            String emailSubject = "Confirmation de votre demande de retrait";
            String emailBody = "Bonjour " + currentProvider.getFirstName() + ",\n\n" +
                    "Votre demande de retrait de " + dto.getAmount() + " FCFA a été enregistrée avec succès.\n" +
                    "Référence : " + retrait.getReference() + "\n" +
                    "Méthode : " + dto.getMethod() + "\n" +
                    "Numéro : " + dto.getPhoneNumber() + "\n\n" +
                    "Votre demande est en attente de validation par l'administrateur.\n\n" +
                    "Cordialement,\nL'équipe Solimus";
            emailService.sendEmail(currentProvider.getEmail(), emailSubject, emailBody);
        }

        //Retourner la réponse
        return mapToWithdrawalDTO(retrait);
    }

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    /**
     * Crée un nouveau portefeuille pour un prestataire donné (sécurité).
     */
    private ProviderWallet createWallet(Long providerId) {

        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Prestataire introuvable"));
        return ProviderWallet.builder()
                .provider(provider)
                .build();
    }

    /**
     * Génère une référence unique pour un retrait.
     */
    private String generateReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }

    /**
     * Mappe une WithdrawalRequest vers son DTO pour la réponse.
     */
    private WithdrawalRequestDTO mapToWithdrawalDTO(ProviderWithdrawalRequest retrait) {
        return WithdrawalRequestDTO.builder()
            .id(retrait.getId())
            .reference(retrait.getReference())
            .amount(retrait.getAmount())
            .method(retrait.getMethod())
            .phoneNumber(retrait.getPhoneNumber())
            .status(retrait.getStatus())
            .createdAt(retrait.getCreatedAt())
            .build();
    }

    /**
     * Fusionne les paiements et les retraits d'un prestataire, triés par date décroissante,
     * directement paginés en base (UNION ALL natif — voir PaymentRepository.findProviderWalletTransactionsUnion).
     */
    private Page<WalletTransactionDTO> getTransactions(Long providerId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> rowsPage = paymentRepository.findProviderWalletTransactionsUnion(providerId, pageable);

        return rowsPage.map(row -> WalletTransactionDTO.builder()
                .label((String) row[0])
                .amount((BigDecimal) row[1])
                .type(TransactionType.valueOf((String) row[2]))
                .status((String) row[3])
                .date(toLocalDate(row[4]))
                .build());
    }

    // Convertit la colonne "transaction_date" (renvoyée en Timestamp/LocalDateTime selon le driver JDBC)
    // en LocalDate, tel qu'attendu par WalletTransactionDTO.date
    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return null;
    }

    /**
     * Récupère l'utilisateur (prestataire) actuellement authentifié via le contexte de sécurité Spring.
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
