package com.example.solimus.services.syndic.settings;

import com.example.solimus.dtos.syndic.settings.ChangePasswordDTO;
import com.example.solimus.dtos.syndic.settings.CreateFacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreatePropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreateSecurityFeatureDTO;
import com.example.solimus.dtos.syndic.settings.CreateSpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.PropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.SecurityFeatureDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.SyndicFinancialSettingsDTO;
import com.example.solimus.dtos.syndic.settings.SyndicProfileDTO;
import com.example.solimus.dtos.syndic.settings.UpdateSecurityFeatureDTO;
import com.example.solimus.dtos.syndic.settings.UpdateSyndicFinancialSettingsDTO;
import com.example.solimus.dtos.syndic.settings.UpdateSyndicProfileDTO;
import com.example.solimus.enums.FacilityCategory;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyndicSettingsService {

    //--------------------------------------------------
    // ===== TYPES D'ÉQUIPEMENTS =====
    //--------------------------------------------------
    Page<FacilityTypeDTO> getAllFacilityTypes(int page, int size);

    void createFacilityType(String name, String category, String description, Boolean isActive, MultipartFile icon);

    void updateFacilityType(Long id, String name, String category, String description, Boolean isActive, MultipartFile icon);

    void deleteFacilityType(Long id);

    //--------------------------------------------------
    // ===== SPÉCIALITÉS =====
    //--------------------------------------------------
    Page<SpecialtyDTO> getAllSpecialties(int page, int size);

    void createSpecialty(String name, String description, MultipartFile icon);

    void updateSpecialty(Long id, String name, String description, MultipartFile icon);

    void deleteSpecialty(Long id);

    //--------------------------------------------------
    // ===== TYPES D'APPARTEMENT =====
    //--------------------------------------------------
    Page<PropertyTypeDTO> getAllPropertyTypes(int page, int size);

    void createPropertyType(CreatePropertyTypeDTO dto);

    void updatePropertyType(Long id, CreatePropertyTypeDTO dto);

    void deletePropertyType(Long id);

    //--------------------------------------------------
    // ===== OPTIONS DE SÉCURITÉ =====
    //--------------------------------------------------
    Page<SecurityFeatureDTO> getAllSecurityFeatures(int page, int size);

    void createSecurityFeature(String label, String description, Boolean isActive, MultipartFile icon);

    void updateSecurityFeature(Long id, String label, String description, Boolean isActive, MultipartFile icon);

    void deleteSecurityFeature(Long id);

    //--------------------------------------------------
    // ===== Paramètres financiers =====
    //--------------------------------------------------

    /**
     * Récupère les paramètres financiers du syndic connecté.
     * Si jamais configurés, retourne les valeurs par défaut
     * (FCFA, TRIMESTRIEL, 1.5%, 30 jours, 5%) sans les persister en base.
     */
    SyndicFinancialSettingsDTO getFinancialSettings();

    /**
     * Crée ou met à jour les paramètres financiers du syndic connecté.
     * Un seul syndic = une seule configuration (logique create-or-update,
     */
    void saveFinancialSettings(UpdateSyndicFinancialSettingsDTO dto);

    //--------------------------------------------------
    // ===== PROFIL SYNDIC =====
    //--------------------------------------------------

    /**
     * Récupère le profil du syndic connecté.
     */
    SyndicProfileDTO getSyndicProfile();

    /**
     * Met à jour le profil du syndic connecté.
     * Seuls les champs non null sont mis à jour.
     */
    void updateSyndicProfile(String firstName, String lastName, String phone, MultipartFile photo);

    /**
     * Ajoute ou remplace la photo de profil du syndic.
     */
    void updateProfilePhoto(MultipartFile photo);

    /**
     * Change le mot de passe du syndic connecté.
     * Vérifie que le mot de passe actuel est correct avant de le changer.
     */
    void changePassword(ChangePasswordDTO dto);

}
