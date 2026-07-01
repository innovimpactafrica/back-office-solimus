package com.example.solimus.dtos.syndic.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO output profil syndic 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicProfileDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String photoUrl;
}
