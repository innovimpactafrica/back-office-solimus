package com.example.solimus.config;

import com.example.solimus.entities.Role;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.repositories.RoleRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cette classe s'exécute automatiquement au démarrage de l'application.
 * Elle sert à pré-remplir la base de données avec les données indispensables (Rôles, Admin).
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // Récupère les identifiants admin depuis application.properties ou utilise les valeurs par défaut
    @Value("${admin.default.email:adminsolimus@yopmail.com}")
    private String adminEmail;

    @Value("${admin.default.password:Passer@123}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        
        // 1. INITIALISATION DES RÔLES
        // On boucle sur l'Enum ERole pour s'assurer que chaque rôle existe dans la table 'roles'
        for (ERole eRole : ERole.values()) {
            if (roleRepository.findByName(eRole).isEmpty()) {
                Role role = new Role();
                role.setName(eRole);
                role.setDescription("Rôle par défaut pour " + eRole.getLabel());
                roleRepository.save(role);
                System.out.println("🔧 Rôle créé : " + eRole.name());
            }
        }

        // 2. INITIALISATION DE L'ADMINISTRATEUR
        // On vérifie si un administrateur est déjà présent pour éviter les doublons
        if (!userRepository.existsByRole_Name(ERole.ROLE_ADMIN)) {
            
            // On récupère l'entité Role correspondant à ADMIN
            Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Erreur critique : Rôle ADMIN non trouvé"));

            // Création de l'objet User Admin
            User admin = new User();
            admin.setFirstName("Admin");
            admin.setLastName("Solimus");
            admin.setEmail(adminEmail);
            admin.setPhone("+221770000000");
            admin.setPassword(passwordEncoder.encode(adminPassword)); // Le mot de passe est crypté
            admin.setRole(adminRole);
            admin.setStatus(UserStatus.ACTIVE); // L'admin est actif par défaut

            userRepository.save(admin);

            System.out.println("✅ Administrateur par défaut créé avec succès !");
            System.out.println("📧 Email: " + adminEmail);
            System.out.println("🔑 Mot de passe: " + adminPassword);
        } else {
            System.out.println("ℹ️ Système : L'administrateur est déjà présent.");
        }
    }
}
