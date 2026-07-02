package com.example.solimus.services.syndic.residence;

import com.example.solimus.dtos.syndic.residence.*;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyndicResidenceService {

    // Étape 1 — Créer la résidence avec les infos générales et photo
    // Retourne l'ID de la résidence créée pour les étapes suivantes
    ResidenceDTO createResidenceComplete(CreateResidenceDTO dto, MultipartFile photo);

    // Étape 2 — Ajouter un lot/appartement à la résidence
    PropertyDTO addProperty(Long residenceId, AddPropertyDTO dto);

    // Étape 2 — Modifier un lot/appartement
    PropertyDTO updateProperty(Long residenceId, Long propertyId, UpdatePropertyDTO dto);

    // Étape 2 — Supprimer un lot/appartement
    void deleteProperty(Long residenceId, Long propertyId);

    // Étape 2 — Lister les lots d'une résidence (paginé)
    Page<PropertyListDTO> getPropertiesPaginated(Long residenceId, Integer page, Integer size);

    //Étape 2 _ Lister tous les types de biens (pour dropdown lors de la création d'un lot)
    List<PropertyTypeDTO> getAllPropertyTypes();

    //  Étape 2 — Lister les copropriétaires pour l'affectation d'un lot
    List<CoOwnerSelectionDTO> searchCoOwnersForSelection(String search);

    //  Étape 3 — Lister les types d'équipement avec leurs champs
    List<FacilityTypeDTO> getFacilityTypes();

    //  Étape 3 — Ajouter un équipement commun à une résidence
    void addFacility(Long residenceId, AddFacilityDTO dto);

    //  Étape 3 — Mettre à jour les options de sécurité d'une résidence
    void updateSecurityFeatures(Long residenceId, UpdateSecurityFeaturesDTO dto);

    // Lister les résidences du syndic connecté
    List<ResidenceDTO> getMesResidences();

    // Détail complet d'une résidence
    ResidenceDTO getResidenceDetail(Long residenceId);
}