package com.example.solimus.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 409 générique — utilisé quand l'état actuel d'une ressource empêche l'action demandée
// (ex: une demande de retrait déjà traitée qu'on tente de valider/refuser une seconde fois)
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}