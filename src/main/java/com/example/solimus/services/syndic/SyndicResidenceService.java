package com.example.solimus.services.syndic;

import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.residence.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SyndicResidenceService {

    // Étape 1 — Créer la résidence avec les infos générales
    // Retourne l'ID de la résidence créée pour les étapes suivantes
    ResidenceDTO createResidence(CreateResidenceDTO dto);

    // Étape 1 — Uploader la photo principale
    ResidenceDTO uploadPhoto(Long residenceId, MultipartFile photo);

    // Étape 2 — Ajouter un lot/appartement à la résidence
    PropertyDTO addProperty(Long residenceId, AddPropertyDTO dto);

    // Ajouter un contact clé
    AddResidenceContactDTO addContact(Long residenceId, AddResidenceContactDTO dto);

    // Lister les lots d'une résidence
    List<PropertyListDTO> getPropertiesByResidence(Long residenceId);

    // Compter les lots d'une résidence
    long countPropertiesByResidence(Long residenceId);

    // Modifier un lot d'une résidence
    PropertyListDTO updateProperty(Long residenceId, Long propertyId, UpdatePropertyDTO dto);

    // Supprimer un lot d'une résidence
    void deleteProperty(Long residenceId, Long propertyId);

    // Retirer un copropriétaire d'un lot
    PropertyListDTO removeOwnerFromProperty(Long residenceId, Long propertyId);

    // Affecter un copropriétaire à un lot
    PropertyListDTO assignOwnerToProperty(Long residenceId, Long propertyId, Long ownerId);

    // Lister les copropriétaires pour l'affectation d'un lot
    List<CoOwnerSelectionDTO> searchCoOwnersForSelection(String search);

    // Lister les résidences du syndic connecté
    List<ResidenceDTO> getMesResidences();

    // Détail complet d'une résidence
    ResidenceDTO getResidenceDetail(Long residenceId);

    // Lister les options de sécurité actives pour le syndic
    List<SecurityFeatureSimpleDTO> getActiveSecurityFeatures();

    // Ajouter les options de sécurité à une résidence (Étape 3)
    void addSecurityFeatures(Long residenceId, AddSecurityFeaturesDTO dto);

}