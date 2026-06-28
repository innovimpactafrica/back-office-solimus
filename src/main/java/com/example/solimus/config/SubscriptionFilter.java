package com.example.solimus.config;

import com.example.solimus.entities.Subscription;
import com.example.solimus.entities.User;
import com.example.solimus.repositories.SubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

//Classe qui permet de bloquer l'accès aux routes /api/provider/** si l'utilisateur n'a pas d'abonnement actif
@Component
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper; // pour écrire la réponse JSON en cas de blocage

    // routes /api/provider/** accessibles sans abonnement actif
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/provider/subscription/plan",
            "/api/provider/subscription/initiate"
    );

    //Méthode implémentée de OncePerRequestFilter pour filtrer les requêtes et vérifier si l'utilisateur a un abonnement actif
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI(); // on récupère l'URL de la requête entrante

        // si la route ne commence pas par /api/provider/, on laisse passer sans vérification
        if (!path.startsWith("/api/provider/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // si la route est dans la liste des exclusions, on laisse passer
        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }
        //* sinon
        // on récupère l'authentification depuis le SecurityContext — déjà rempli par le filtre JWT
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // si pas d'authentification, on laisse Spring Security gérer (il bloquera lui-même)
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = authentication.getName(); // on récupère l'email de l'utilisateur connecté

        // on cherche l'utilisateur en base pour récupérer son ID
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // email du JWT ne correspond à aucun utilisateur en base — cas anormal, on bloque
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    Map.of("message", "Utilisateur introuvable.")
            );
            return;
        }

        // on cherche le dernier abonnement de cet utilisateur via son ID
        boolean isActive = subscriptionRepository
                .findFirstByProviderIdOrderByEndDateDesc(user.getId()) // on utilise l'ID du provider
                .map(Subscription::isCurrentlyActive) // on vérifie s'il est encore valide
                .orElse(false); // pas d'abonnement = inactif

        if (!isActive) {
            // on bloque et on retourne une erreur 403 avec un message JSON clair
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    Map.of("message", "Abonnement inactif. Veuillez renouveler votre abonnement pour accéder à cette fonctionnalité.")
            );
            return;
        }

        filterChain.doFilter(request, response); // abonnement actif — on laisse passer
    }
}
