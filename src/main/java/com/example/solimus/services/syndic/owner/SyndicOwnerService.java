package com.example.solimus.services.syndic.owner;

import com.example.solimus.dtos.owner.CoOwnerInterventionsResponseDTO;
import com.example.solimus.dtos.owner.CoOwnerMeetingsDTO;
import com.example.solimus.dtos.owner.CoOwnerResidenceDTO;
import com.example.solimus.dtos.syndic.owner.*;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.enums.Nationality;
import com.example.solimus.enums.Title;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface SyndicOwnerService {

    /** Créer un copropriétaire avec son profil et affecter les biens */
    void addCoOwner(CreateCoOwnerDTO dto, MultipartFile photo);

    /** Lister les biens disponibles (VACANT) d'une résidence */
    Page<PropertySummaryDTO> getAvailableProperties(Long residenceId, Integer page, Integer size);

    /** Lister les résidences qui ont au moins un bien vacant */
    Page<ResidenceSummaryDTO> getResidencesWithVacantProperties(Integer page, Integer size);

    /** Lister les résidences d'un copropriétaire (pour le filtre finances) */
    Page<CoOwnerResidenceDTO> getCoOwnerResidences(Long coOwnerId, Integer page, Integer size);

    /** Lister les copropriétaires des résidences du syndic connecté, avec recherche, filtre résidence et statut */
    Page<CoOwnerListDTO> getCoOwners(String search, Long residenceId, String status, Integer page, Integer size);

    /** Détail d'un copropriétaire (en-tête + KPIs) */
    CoOwnerDetailDTO getCoOwnerDetail(Long coOwnerId);

    /** Lister les lots d'un copropriétaire (onglet Appartements du détail) */
    Page<CoOwnerPropertyItemDTO> getCoOwnerProperties(Long coOwnerId, Integer page, Integer size);

    /** Finances d'un copropriétaire pour une résidence (onglet Finances du détail) */
    CoOwnerFinancesDTO getCoOwnerFinances(Long coOwnerId, Long residenceId);

    /** Historique des paiements d'un copropriétaire (onglet Paiements du détail) */
    Page<CoOwnerPaymentItemDTO> getCoOwnerPayments(Long coOwnerId, String status, Integer page, Integer size);

    /** Assemblées Générales d'un copropriétaire (onglet AG du détail) */
    CoOwnerMeetingsDTO getCoOwnerMeetings(Long coOwnerId, Long residenceId, String type, Integer year, Integer page, Integer size);

    /** Travaux d'un copropriétaire (onglet Travaux du détail) */
    CoOwnerInterventionsResponseDTO getCoOwnerInterventions(Long coOwnerId);

    /** Documents d'un copropriétaire, toutes sources confondues (manuel, AG, charges exceptionnelles)(onglet Documents du détail) */
    CoOwnerDocumentUnifiedListResponseDTO getCoOwnerDocuments(Long coOwnerId, String search, String category,
                                                              int page, int size);

    /** Activité récente d'un copropriétaire (panneau Activité Récente du détail) */
    Page<ActivityLogItemDTO> getCoOwnerActivityLog(Long coOwnerId, Integer page, Integer size);

    /** Recherche un copropriétaire par nom complet, email ou téléphone — pour l'autocomplete */
    Page<CoOwnerSearchResultDTO> searchCoOwners(String q, Integer page, Integer size);

    /** Lier un copropriétaire existant au syndic connecté via la relation */
     void linkCoOwner(Long coOwnerId);

    /** Met à jour partiellement un copropriétaire (seuls les champs fournis sont modifiés) */
    void updateCoOwner(Long coOwnerId, String firstName, String lastName, String email, String phone,
                       Title title, LocalDate birthDate, Nationality nationality, String secondaryPhone, String address);

    /** Supprime un copropriétaire et libère ses lots (statut VACANT) */
    void deleteCoOwner(Long coOwnerId);
}
