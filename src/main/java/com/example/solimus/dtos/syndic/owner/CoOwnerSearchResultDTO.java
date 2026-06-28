package com.example.solimus.dtos.syndic.owner;

import com.example.solimus.enums.Nationality;
import com.example.solimus.enums.Title;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

//Dto pour la recherche de copropriétaires
@Data
@Builder
public class CoOwnerSearchResultDTO {

    private Long id; // id du User — utile pour le link directement

    private String fullName; // prénom + nom — affiché dans la liste de suggestions

    private String firstName; // prénom — pré-rempli dans le formulaire

    private String lastName; // nom — pré-rempli dans le formulaire

    private String email; // affiché dans la suggestion ET pré-rempli dans le formulaire

    private String phone; // affiché dans la suggestion ET pré-rempli dans le formulaire

    private String photoUrl; // avatar affiché dans la suggestion ET pré-rempli dans le formulaire

    private Title title; // civilité — pré-rempli dans le formulaire

    private LocalDate birthDate; // date de naissance — pré-rempli dans le formulaire

    private Nationality nationality; // nationalité — pré-rempli dans le formulaire

    private String secondaryPhone; // téléphone secondaire — pré-rempli dans le formulaire

    private String address; // adresse personnelle — pré-rempli dans le formulaire
}