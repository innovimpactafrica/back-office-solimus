package com.example.solimus.services.owner;

import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;

import java.util.List;

public interface OwnerService {

    // =========================================================================
    // RÉSIDENCES (Dropdown pour l'inscription)
    // =========================================================================
    List<ResidenceDTO> getAllResidences();

    // =========================================================================
    // BIENS (Dropdown pour l'inscription)
    // =========================================================================
    List<PropertyDTO> getPropertiesByResidence(Long residenceId);
}
