package com.example.solimus.services.provider.wallet;

import com.example.solimus.dtos.provider.wallet.RequestWithdrawalDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;
import com.example.solimus.dtos.provider.wallet.WalletTransactionDTO;
import com.example.solimus.entities.PaymentProvider;
import com.example.solimus.entities.User;
import com.example.solimus.entities.ProviderWallet;
import com.example.solimus.entities.ProviderWalletTransaction;
import com.example.solimus.entities.ProviderWithdrawalRequest;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.ProviderWalletTransactionCategory;
import com.example.solimus.enums.TransactionType;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.exceptions.BadRequestException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

        // 1. Vérifier que le solde disponible (recalculé à la volée) est suffisant
        BigDecimal availableBalance = walletBalanceService.getCurrentBalance(currentProvider.getId());
        if (dto.getAmount().compareTo(availableBalance) > 0) {
            throw new BadRequestException(
                    "Solde insuffisant. Disponible : " + availableBalance + " FCFA");
        }

        // 2. Créer la demande de versement (retrait)
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
     * Fusionne les paiements et les retraits, les mappe en DTO et pagine le résultat.
     */
    private Page<WalletTransactionDTO> getTransactions(Long providerId, int page, int size) {

        // 1. Récupérer tous les paiements reçus
        List<PaymentProvider> paiements = paymentRepository.findAllByProviderIdOrderByCreatedAtDesc(providerId);

        // 2. Récupérer tous les retraits
        List<ProviderWithdrawalRequest> retraits = withdrawalRequestRepository.findAllByProviderIdOrderByCreatedAtDesc(providerId);

        // Liste fusionnée contenant paiements et retraits
        List<WalletTransactionDTO> transactions = new ArrayList<>();

        // 3. Ajouter les paiements (crédits)
        if (paiements != null) {

            // Parcourir chaque paiement pour le convertir en transaction
            paiements.forEach(p -> {

                // Récupérer le nom de la résidence (ou valeur par défaut)
                String residenceName = p.getInterventionRequest().getResidence() != null
                        ? p.getInterventionRequest().getResidence().getName()
                        : "Résidence";
                //Récupérer le nom de la spécialité ou valeur par défaut
                String specialtyName = p.getInterventionRequest().getSpecialty() != null
                        ? p.getInterventionRequest().getSpecialty().getName()
                        : "Intervention";

                // Créer et ajouter la transaction de paiement
                transactions.add(WalletTransactionDTO.builder()
                        .label(residenceName + " - " + specialtyName)
                        .amount(p.getAmount())
                        .type(TransactionType.ENTREE)
                        .status(p.getStatus() == PaymentStatus.COMPLETED ? "Reçu" : "En attente")
                        .date(p.getCreatedAt().toLocalDate())
                        .build());
            });
        }

        // 4. Ajouter les retraits (débits)
        if (retraits != null) {
            // Parcourir chaque retrait pour le convertir en transaction
            retraits.forEach(r -> {
                // Déterminer le label de la méthode de retrait
                String methodeLabel = r.getMethod() != null ? r.getMethod().name() : "N/A";
                String statutLabel = "En attente";
                if (r.getStatus() == WithdrawalStatus.COMPLETED) {
                    statutLabel = "Effectué";
                } else if (r.getStatus() == WithdrawalStatus.REJECTED) {
                    statutLabel = "Refusé";
                }

                // Créer et ajouter la transaction de retrait
                transactions.add(WalletTransactionDTO.builder()
                        .label("Retrait " + methodeLabel)
                        .amount(r.getAmount().negate())
                        .type(TransactionType.SORTIE)
                        .status(statutLabel)
                        .date(r.getCreatedAt().toLocalDate())
                        .build());
            });
        }

        // 5. Trier par date décroissante
        transactions.sort(Comparator.comparing(WalletTransactionDTO::getDate).reversed());

        // 6. Paginer la liste fusionnée
        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), transactions.size());
        List<WalletTransactionDTO> pagedTransactions = transactions.subList(start, end);

        // pagedTransactions -> liste des transactions à afficher dans cette page
        // pageable -> infos pagination (numéro page, taille page, tri)
        // transactions.size() -> nombre total de transactions (pour calculer nombre total de pages)
        return new PageImpl<>(pagedTransactions, pageable, transactions.size());
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
