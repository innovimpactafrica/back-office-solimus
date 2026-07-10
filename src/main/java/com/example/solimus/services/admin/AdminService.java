package com.example.solimus.services.admin;

import com.example.solimus.dtos.admin.*;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanDTO;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanRequestDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service de gestion administrative.
 */
public interface AdminService {

    // --- GESTION DES RETRAITS WALLET ---

    /**
     * Valide une demande de retrait wallet après paiement réel du demandeur.
     */
    WithdrawalRequestDTO approveWalletWithdrawal(Long withdrawalId, MultipartFile receipt, String comment);

    /**
     * Refuse une demande de retrait wallet et remet le montant dans le solde disponible.
     */
    WithdrawalRequestDTO rejectWalletWithdrawal(Long withdrawalId, String rejectionReason);


    // --- GESTION ABONNEMENTS PRESTATAIRES ---
    /**
     * Crée ou met à jour la formule prestataire (save transparent).
     * Si aucune formule n'existe encore, elle est créée.
     * Si une formule existe déjà, elle est mise à jour avec les nouvelles valeurs.
     */
    ProviderPlanDTO saveProviderPlan(ProviderPlanRequestDTO dto);

    // --- GESTION DES DÉLAIS ---
    List<EstimatedDelayDTO> getAllEstimatedDelays();
    EstimatedDelayDTO addEstimatedDelay(String label, Integer days);
    void deleteEstimatedDelay(Long id);

    // --- GESTION DES UTILISATEURS ---
    /**
     * Liste paginée des utilisateurs avec filtres.
     * @param page Numéro de la page (0-indexed)
     * @param size Taille de la page
     * @param search Recherche par nom, prénom ou email
     * @param role Filtre par rôle (optionnel)
     * @param status Filtre par statut (optionnel)
     */
    UserListResponseDTO getUsers(int page, int size, String search, ERole role, UserStatus status);

    /**
     * Modifie le statut d'un utilisateur
     */
    void updateUserStatus(Long userId, UserStatus status);

    /**
     * Crée un compte utilisateur par l'admin (Syndic, etc.).
     * Le compte est créé avec isActive=false, statut=PENDING et sans mot de passe.
     * Un email d'activation est automatiquement envoyé.
     *
     * @param dto Les informations de l'utilisateur saisies par l'admin.
     * @return Le DTO du compte créé avec confirmation d'envoi d'email.
     */
   CreateUserResponseDTO createUser(
            CreateUserRequestDTO dto);


}
