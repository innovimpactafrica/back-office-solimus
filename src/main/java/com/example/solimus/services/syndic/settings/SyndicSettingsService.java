package com.example.solimus.services.syndic.settings;

import com.example.solimus.dtos.syndic.settings.CreateFacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreatePropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreateSpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.PropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.SyndicFinancialSettingsDTO;

import java.util.List;

public interface SyndicSettingsService {

    //--------------------------------------------------
    // ===== TYPES D'ÉQUIPEMENTS =====
    //--------------------------------------------------
    List<FacilityTypeDTO> getAllFacilityTypes();

    void createFacilityType(CreateFacilityTypeDTO dto);

    void updateFacilityType(Long id, CreateFacilityTypeDTO dto);

    void deleteFacilityType(Long id);

    //--------------------------------------------------
    // ===== SPÉCIALITÉS =====
    //--------------------------------------------------
    List<SpecialtyDTO> getAllSpecialties();

    void createSpecialty(CreateSpecialtyDTO dto);

    void updateSpecialty(Long id, CreateSpecialtyDTO dto);

    void deleteSpecialty(Long id);

    //--------------------------------------------------
    // ===== TYPES D'APPARTEMENT =====
    //--------------------------------------------------
    List<PropertyTypeDTO> getAllPropertyTypes();

    void createPropertyType(CreatePropertyTypeDTO dto);

    void updatePropertyType(Long id, CreatePropertyTypeDTO dto);

    void deletePropertyType(Long id);

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
    void saveFinancialSettings(SyndicFinancialSettingsDTO dto);

}
