package com.example.solimus.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

// Endpoint TEMPORAIRE de diagnostic — vérifie si le mot de passe mail arrive entier ou coupé.
// Ne renvoie jamais le mot de passe complet. À supprimer une fois le problème résolu.
@RestController
@Tag(name = "Diagnostic (temporaire)", description = "Vérifie la longueur du mot de passe mail reçu — à supprimer après usage")
public class DiagnosticsController {

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Operation(summary = "Vérifie si le mot de passe mail est arrivé complet (longueur + 2 premiers/derniers caractères, jamais le mot de passe entier)")
    @GetMapping("/api/diagnostics/mail-password-check")
    public ResponseEntity<Map<String, Object>> checkMailPassword() {

        Map<String, Object> result = new LinkedHashMap<>();

        int length = mailPassword == null ? 0 : mailPassword.length();
        result.put("length", length);
        result.put("startsWith", length >= 2 ? mailPassword.substring(0, 2) : mailPassword);
        result.put("endsWith", length >= 2 ? mailPassword.substring(length - 2) : mailPassword);

        return ResponseEntity.ok(result);
    }
}
