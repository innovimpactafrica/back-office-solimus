package com.example.solimus.services.admin;

import com.example.solimus.dtos.admin.*;
import com.example.solimus.dtos.residence.CreateSecurityFeatureDTO;
import com.example.solimus.dtos.residence.SecurityFeatureDTO;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;

import java.util.List;

/**
 * Service de gestion administrative.
 * Gère le référentiel (Spécialités, Zones) et les utilisateurs (Listing, Activation).
 */
public interface AdminService {

    // --- GESTION DES DÉLAIS ---
    List<EstimatedDelayDTO> getAllEstimatedDelays();
    EstimatedDelayDTO addEstimatedDelay(String label, Integer days);

    // --- GESTION DES SPÉCIALITÉS ---
    List<SpecialtyDTO> getAllSpecialties();
    SpecialtyDTO createSpecialty(CreateSpecialtyDTO specialtyDTO);
    SpecialtyDTO updateSpecialty(Long id, CreateSpecialtyDTO specialtyDTO);
    void deleteSpecialty(Long id);



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

  // --- GESTION DES OPTIONS DE SÉCURITÉ ---

  /**
   * Créer une nouvelle option de sécurité
   */
  void createSecurityFeature(CreateSecurityFeatureDTO dto);

  /**
   * Lister toutes les options de sécurité (actives ou non)
   */
  List<SecurityFeatureDTO> getSecurityFeatures();

  /**
   * Désactiver une option de sécurité
   */
  void deactivateSecurityFeature(Long id);

  /**
   * Activer une option de sécurité
   */
  void activateSecurityFeature(Long id);
    
}
