package com.example.solimus.services.syndic.owner;

import com.example.solimus.dtos.owner.CoOwnerDocumentItemDTO;
import com.example.solimus.dtos.owner.CoOwnerInterventionsResponseDTO;
import com.example.solimus.dtos.owner.CoOwnerMeetingsDTO;
import com.example.solimus.dtos.syndic.owner.*;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.enums.CoOwnerDocumentCategory;
import com.example.solimus.enums.Nationality;
import com.example.solimus.enums.Title;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface SyndicOwnerService {

    /** Créer un copropriétaire avec son profil et affecter les biens */
    void addCoOwner(CreateCoOwnerDTO dto, MultipartFile photo);

    /** Lister les biens disponibles (VACANT) d'une résidence */
    List<PropertySummaryDTO> getAvailableProperties(Long residenceId);

    /** Lister les résidences qui ont au moins un bien vacant */
    List<ResidenceSummaryDTO> getResidencesWithVacantProperties();

    /** Lister les copropriétaires des résidences du syndic connecté, avec recherche, filtre résidence et statut */
    Page<CoOwnerListDTO> getCoOwners(String search, Long residenceId, String status, Integer page, Integer size);

    /** Détail d'un copropriétaire (en-tête + KPIs) */
    CoOwnerDetailDTO getCoOwnerDetail(Long coOwnerId);

    /** Lister les lots d'un copropriétaire (onglet Appartements du détail) */
    List<CoOwnerPropertyItemDTO> getCoOwnerProperties(Long coOwnerId);

    /** Finances d'un copropriétaire pour une résidence (onglet Finances du détail) */
    CoOwnerFinancesDTO getCoOwnerFinances(Long coOwnerId, Long residenceId);

    /** Historique des paiements d'un copropriétaire (onglet Paiements du détail) */
    Page<CoOwnerPaymentItemDTO> getCoOwnerPayments(Long coOwnerId, String status, Integer page, Integer size);

    /** Assemblées Générales d'un copropriétaire (onglet AG du détail) */
    CoOwnerMeetingsDTO getCoOwnerMeetings(Long coOwnerId);

    /** Travaux d'un copropriétaire (onglet Travaux du détail) */
    CoOwnerInterventionsResponseDTO getCoOwnerInterventions(Long coOwnerId);

    /** Documents d'un copropriétaire (onglet Documents du détail) */
    List<CoOwnerDocumentItemDTO> getCoOwnerDocuments(Long coOwnerId, String category);

    /** Ajouter un document à un copropriétaire */
    CoOwnerDocumentItemDTO addDocument(Long coOwnerId, CoOwnerDocumentCategory category, String title, MultipartFile file);

    /** Activité récente d'un copropriétaire (panneau Activité Récente du détail) */
    Page<ActivityLogItemDTO> getCoOwnerActivityLog(Long coOwnerId, Integer page, Integer size);

    /** Recherche un copropriétaire par nom complet, email ou téléphone — pour l'autocomplete */
    List<CoOwnerSearchResultDTO> searchCoOwners(String q);

    /** Lier un copropriétaire existant au syndic connecté via la relation */
     void linkCoOwner(Long coOwnerId);

    /** Met à jour partiellement un copropriétaire (seuls les champs fournis sont modifiés) */
    void updateCoOwner(Long coOwnerId, String firstName, String lastName, String email, String phone,
                       Title title, LocalDate birthDate, Nationality nationality, String secondaryPhone, String address);

    /** Supprime un copropriétaire et libère ses lots (statut VACANT) */
    void deleteCoOwner(Long coOwnerId);
}
