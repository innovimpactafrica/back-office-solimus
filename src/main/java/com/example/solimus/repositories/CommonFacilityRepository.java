package com.example.solimus.repositories;

import com.example.solimus.entities.CommonFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommonFacilityRepository extends JpaRepository<CommonFacility, Long> {

    /**
     * Récupérer tous les équipements d'une résidence. (Création Intervention)
     * Une résidence ne peut avoir qu'un seul équipement par type.
     *
     * @param residenceId identifiant de la résidence
     * @return liste des équipements de la résidence
     */
    List<CommonFacility> findByResidenceId(Long residenceId);

    /**
     * Trouver un équipement par résidence et type (pour create-or-update).
     * Une résidence ne peut avoir qu'un seul équipement par type.
     *
     * @param residenceId identifiant de la résidence
     * @param facilityTypeId identifiant du type d'équipement
     * @return l'équipement existant ou vide
     */
    Optional<CommonFacility> findByResidenceIdAndFacilityTypeId(Long residenceId, Long facilityTypeId);

    /**
     * Compte le nombre de résidences qui possèdent un équipement du type spécifié.
     *
     * @param facilityTypeId identifiant du type d'équipement
     * @return nombre de résidences utilisant ce type d'équipement
     */
    @Query("SELECT COUNT(cf.residence.id) FROM CommonFacility cf WHERE cf.facilityType.id = :facilityTypeId")
    int countResidenceByFacilityTypeId(@Param("facilityTypeId") Long facilityTypeId);

    /**
     * Recherche d'équipements par résidence et nom de type contenant la requête (insensible à la casse).
     * Pour autocomplétion des postes budgétaires.
     *
     * @param residenceId identifiant de la résidence
     * @param namePart partie du nom à rechercher
     * @return liste des équipements correspondants
     */
    List<CommonFacility> findByResidenceIdAndFacilityTypeNameContainingIgnoreCase(Long residenceId, String namePart);
}