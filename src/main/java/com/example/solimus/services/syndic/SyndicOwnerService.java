package com.example.solimus.services.syndic;

import com.example.solimus.dtos.owner.CreateCoOwnerDTO;
import com.example.solimus.dtos.owner.PropertySummaryDTO;
import com.example.solimus.dtos.owner.ResidenceSummaryDTO;

import java.util.List;

public interface SyndicOwnerService {

    /** Créer un copropriétaire avec son profil et affecter les biens */
    void addCoOwner(CreateCoOwnerDTO dto);

    /** Lister les biens disponibles (VACANT) d'une résidence */
    List<PropertySummaryDTO> getAvailableProperties(Long residenceId);

    /** Lister les résidences qui ont au moins un bien vacant */
    List<ResidenceSummaryDTO> getResidencesWithVacantProperties();
}
