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

    // Étape 2 — Lister les lots d'une résidence avec filtres (paginé, pour onglet Appartements)
    Page<PropertyListItemDTO> getPropertiesPaginatedWithFilters(
            Long residenceId, String search, Integer floor, String status, Integer page, Integer size);

    // Lister les équipements communs d'une résidence avec filtres (onglet Biens communs)
    List<CommonFacilityListItemDTO> getCommonFacilitiesWithFilters(
            Long residenceId, String search, String status);

    // Détail d'un équipement commun (onglet Biens communs)
    CommonFacilityDetailDTO getCommonFacilityDetail(Long residenceId, Long facilityId);

    // Kanban des interventions (onglet Travaux)
    InterventionKanbanResponseDTO getInterventionsKanban(Long residenceId);

    // Évolution mensuelle des paiements collectés (onglet Finances)
    List<MonthlyPaymentDTO> getMonthlyPaymentsEvolution(Long residenceId, Integer year);

    // Répartition du budget prévisionnel par catégorie (onglet Finances)
    ExpenseBreakdownDTO getExpensesBreakdown(Long residenceId, Integer year);

    // Liste des appels de charges par copropriétaire (onglet Finances)
    List<ChargeCallItemSummaryDTO> getChargeCallsSummary(Long residenceId);

    // Liste des transactions récentes du wallet (onglet Finances)
    List<WalletTransactionDTO> getRecentWalletTransactions(Long residenceId, Integer limit);

    // Modifier les informations générales d'une résidence (mise à jour partielle)
    void updateResidence(Long residenceId, CreateResidenceDTO dto, MultipartFile photo);

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

    //  Étape 3 — Sauvegarder l'étape 3 complète (équipements + sécurité)
    void saveStep3(Long residenceId, Step3DTO dto);

    // Lister les résidences du syndic connecté (pour dropdowns)
    List<ResidenceDTO> getMesResidences();

    // Statistiques du bandeau d'indicateurs (appelé une seule fois au chargement)
    ResidenceHeaderStatsDTO getResidenceStats(Long residenceId);

    // Contenu de l'onglet Vue générale (indépendant du bandeau)
    ResidenceDetailDTO getResidenceGeneralView(Long residenceId);

    // ===== DASHBOARD RÉSIDENCES =====

    // Statistiques globales du dashboard (bandeau de KPIs)
    ResidenceDashboardStatsDTO getDashboardStats();

    // Liste paginée et filtrée des résidences (cartes)
    Page<ResidenceCardDTO> getResidencesPaginated(String search, String city, String status, Integer page, Integer size);
}