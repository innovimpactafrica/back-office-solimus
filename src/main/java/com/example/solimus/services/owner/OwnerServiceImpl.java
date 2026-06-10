package com.example.solimus.services.owner;

import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Residence;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerServiceImpl implements OwnerService {

    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;

    @Override
    public List<ResidenceDTO> getAllResidences() {
        return residenceRepository.findAll().stream()
                .map(this::mapToResidenceDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PropertyDTO> getPropertiesByResidence(Long residenceId) {
        return propertyRepository.findByResidenceId(residenceId).stream()
                .map(this::mapToPropertyDTO)
                .collect(Collectors.toList());
    }

    private ResidenceDTO mapToResidenceDTO(Residence residence) {
        return ResidenceDTO.builder()
                .id(residence.getId())
                .name(residence.getName())
                .fullAddress(residence.getFullAddress())
                .latitude(residence.getLatitude())
                .longitude(residence.getLongitude())
                .lotsCount(residence.getLotsCount())
                .syndicId(residence.getSyndic() != null ? residence.getSyndic().getId() : null)
                .syndicName(residence.getSyndic() != null ? residence.getSyndic().getFirstName() + " " + residence.getSyndic().getLastName() : null)
                .createdAt(residence.getCreatedAt())
                .build();
    }

    private PropertyDTO mapToPropertyDTO(Property property) {
        return PropertyDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .superficie(property.getSuperficie())
                .type(property.getTypeBien())
                .residenceId(property.getResidence() != null ? property.getResidence().getId() : null)
                .residenceName(property.getResidence() != null ? property.getResidence().getName() : null)
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }
}
