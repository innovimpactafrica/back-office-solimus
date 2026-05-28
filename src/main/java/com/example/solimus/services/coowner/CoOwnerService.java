package com.example.solimus.services.coowner;

import com.example.solimus.dtos.property.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;

import java.util.List;

public interface CoOwnerService {

    // =========================================================================
    // RÉSIDENCES (Dropdown pour l'inscription)
    // =========================================================================
    List<ResidenceDTO> getAllResidences();

    // =========================================================================
    // BIENS (Dropdown pour l'inscription)
    // =========================================================================
    List<PropertyDTO> getPropertiesByResidence(Long residenceId);
}
