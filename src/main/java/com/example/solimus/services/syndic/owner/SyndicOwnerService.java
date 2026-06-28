package com.example.solimus.services.syndic.owner;

import com.example.solimus.dtos.syndic.owner.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SyndicOwnerService {

    /** Créer un copropriétaire avec son profil et affecter les biens */
    void addCoOwner(CreateCoOwnerDTO dto);

    /** Lister les biens disponibles (VACANT) d'une résidence */
    List<PropertySummaryDTO> getAvailableProperties(Long residenceId);

    /** Lister les résidences qui ont au moins un bien vacant */
    List<ResidenceSummaryDTO> getResidencesWithVacantProperties();

    /** Lister les copropriétaires des résidences du syndic connecté, avec recherche et pagination */
    Page<CoOwnerListDTO> getCoOwners(String search, Long residenceId, Pageable pageable);

    /** Recherche un copropriétaire par nom complet, email ou téléphone — pour l'autocomplete */
    List<CoOwnerSearchResultDTO> searchCoOwners(String q);

    /** Lier un copropriétaire existant au syndic connecté via la relation */
     void linkCoOwner(Long coOwnerId);
}
