package com.example.solimus.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 409 — le solde disponible actuel ne couvre pas le montant demandé (ex: validation d'un retrait
// dont le solde a baissé entre-temps, suite à la validation d'une autre demande concurrente)
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}