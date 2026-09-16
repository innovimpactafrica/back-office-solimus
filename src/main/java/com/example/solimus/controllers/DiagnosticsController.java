package com.example.solimus.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

// Endpoint TEMPORAIRE de diagnostic réseau — à supprimer une fois le problème d'envoi de mail résolu
@RestController
@Tag(name = "Diagnostic (temporaire)", description = "Test réseau vers OVH — à supprimer après usage")
public class DiagnosticsController {

    @Operation(summary = "Teste si ce serveur arrive à joindre ssl0.ovh.net sur le port 587")
    @GetMapping("/api/diagnostics/mail-connectivity")
    public ResponseEntity<Map<String, Object>> checkMailConnectivity() {

        String host = "ssl0.ovh.net";
        int port = 587;
        int timeoutMs = 5000;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", host);
        result.put("port", port);

        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            result.put("success", true);
            result.put("message", "Connexion réussie — le port n'est pas bloqué");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getClass().getSimpleName() + " : " + e.getMessage());
        }
        result.put("durationMs", System.currentTimeMillis() - start);

        return ResponseEntity.ok(result);
    }
}
