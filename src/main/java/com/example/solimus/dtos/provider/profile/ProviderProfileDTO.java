package com.example.solimus.dtos.provider.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//DTO profil prestataire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfileDTO {

    // Infos affichées en haut
    private String companyName;
    private String specialtyName;
    private String email;
    private String phone;
    private String language;           // "Français, Wolof"
    
    @JsonFormat(pattern = "MMMM yyyy", locale = "fr")
    private LocalDateTime memberSince; // quand est-ce qu'il s'est inscrit?
    
    private String profilePhotoUrl;
}
