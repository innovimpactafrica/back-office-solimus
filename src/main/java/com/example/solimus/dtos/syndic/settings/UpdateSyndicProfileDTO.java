package com.example.solimus.dtos.syndic.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour modifier profil syndic
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSyndicProfileDTO {
    private String firstName;
    private String lastName;
    private String phone;
    private String photoUrl;
}
