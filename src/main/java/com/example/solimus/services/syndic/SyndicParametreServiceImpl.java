package com.example.solimus.services.syndic;

import com.example.solimus.dtos.residence.AddFacilityDTO;
import com.example.solimus.entities.CommonFacility;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.CommonFacilityRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicParametreServiceImpl implements SyndicParametreService {

    private final ResidenceRepository residenceRepository;
    private final CommonFacilityRepository facilityRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AddFacilityDTO addFacility(Long residenceId, AddFacilityDTO dto) {

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ResourceNotFoundException("Vous n'êtes pas autorisé à modifier cette résidence");
        }

        CommonFacility facility = new CommonFacility();
        facility.setFacilityType(dto.getFacilityType());
        facility.setResidence(residence);

        // Copier les champs pertinents selon le type
        facility.setCount(dto.getCount());
        facility.setIsHeated(dto.getIsHeated());
        facility.setCapacity(dto.getCapacity());
        facility.setFloorsCovered(dto.getFloorsCovered());
        facility.setSuperficie(dto.getSuperficie());
        facility.setEtat(dto.getEtat());
        facility.setIndoorSpots(dto.getIndoorSpots());
        facility.setOutdoorSpots(dto.getOutdoorSpots());
        facility.setChargingStations(dto.getChargingStations());
        facility.setPowerKva(dto.getPowerKva());
        facility.setFuelType(dto.getFuelType());
        facility.setCapacityLiters(dto.getCapacityLiters());
        facility.setPumpStatus(dto.getPumpStatus());

        CommonFacility saved = facilityRepository.save(facility);

        log.info("Équipement '{}' ajouté à la résidence '{}'",
                saved.getFacilityType(), residence.getName());

        return dto;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
