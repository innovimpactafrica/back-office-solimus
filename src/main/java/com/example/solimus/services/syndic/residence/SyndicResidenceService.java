package com.example.solimus.services.syndic.residence;

import com.example.solimus.dtos.syndic.residence.*;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyndicResidenceService {

    // Étape 1 — Créer la résidence avec les infos générales
    // Retourne l'ID de la résidence créée pour les étapes suivantes
    ResidenceDTO createResidence(CreateResidenceDTO dto);

    // Étape 1 — Uploader la photo principale
    ResidenceDTO uploadPhoto(Long residenceId, MultipartFile photo);


    // Étape 1 - Ajouter un contact clé
    AddResidenceContactDTO addContact(Long residenceId, AddResidenceContactDTO dto);

    // Étape 2 — Ajouter un lot/appartement à la résidence
    PropertyDTO addProperty(Long residenceId, AddPropertyDTO dto);

    // Étape 2 — Affecter un copropriétaire à un lot
    PropertyListDTO assignOwnerToProperty(Long residenceId, Long propertyId, Long ownerId);

    //Étape 2 _ Lister tous les types de biens (pour dropdown lors de la création d'un lot)
    List<PropertyTypeDTO> getAllPropertyTypes();

    //  Étape 2 — Lister les copropriétaires pour l'affectation d'un lot
    List<CoOwnerSelectionDTO> searchCoOwnersForSelection(String search);

    //  Étape 3 — Lister les types d'équipement avec leurs champs
    List<FacilityTypeDTO> getFacilityTypes();

    //  Étape 3 — Ajouter un équipement commun à une résidence
    void addFacility(Long residenceId, AddFacilityDTO dto);

    // Lister les résidences du syndic connecté
    List<ResidenceDTO> getMesResidences();

    // Détail complet d'une résidence
    ResidenceDTO getResidenceDetail(Long residenceId);




}