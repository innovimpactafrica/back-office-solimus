package com.example.solimus.services.syndic.settings;

import com.example.solimus.dtos.syndic.settings.CreateFacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreatePropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreateSpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.PropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;

import java.util.List;

public interface SyndicParametreService {

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
}
