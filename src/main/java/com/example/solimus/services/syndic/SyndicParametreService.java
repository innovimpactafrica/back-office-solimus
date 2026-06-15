package com.example.solimus.services.syndic;

import com.example.solimus.dtos.residence.AddFacilityDTO;

public interface SyndicParametreService {

    /** Ajouter un équipement commun à une résidence */
    AddFacilityDTO addFacility(Long residenceId, AddFacilityDTO dto);
}
