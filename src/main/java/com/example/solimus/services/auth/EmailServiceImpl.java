package com.example.solimus.services.auth;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Service d'envoi d'emails pour Solimus.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    // =========================================================================
    // CONFIGURATION EMAIL
    // =========================================================================

    @Value("${mail.from:admincoopachat@yopmail.com}")
    private String mailFrom;

    @Value("${mail.app.name:Solimus}")
    private String appName;

    @Value("${app.frontend.activation-url:http://localhost:4200/create-password?token=}")
    private String activationUrl;


    // =========================================================================
    // EMAIL GÉNÉRIQUE
    // =========================================================================
    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body != null ? body : "", false);
            mailSender.send(message);
            log.info("Email envoyé à: {} (sujet: {})", to, subject);
        } catch (Exception e) {
            log.error("Erreur envoi email à {}: {}", to, e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email", e);
        }
    }

    // =========================================================================
    // ACTIVATION ET INSCRIPTION
    // =========================================================================
    @Override
    public void sendActivationCode(String email, String code, String firstName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("Code de vérification - " + appName);

            String body = String.format(
                "Bonjour %s,%n%nVotre code de vérification %s est : %s%n%nCe code expire dans 15 minutes.%n%nSi vous n'êtes pas à l'origine de cette demande, ignorez cet email.%n%nL'équipe %s",
                firstName != null ? firstName : "", appName, code, appName
            );
            
            helper.setText(body, false);
            mailSender.send(message);

            log.info("Code d'activation envoyé avec succès à: {}", email);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du code d'activation à {}: {}", email, e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de vérification", e);
        }
    }

    @Override
    public void sendUserActivationLink(String email, String token, String firstName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("Activez votre compte - " + appName);

            String activationLink = activationUrl + token;

            String textBody = String.format(
                    "Bonjour %s,%n%n" +
                            "L'administrateur %s vous a créé un compte utilisateur.%n%n" +
                            "Pour activer votre compte et définir votre mot de passe, cliquez sur ce lien :%n" +
                            "%s%n%n" +
                            "Ce lien expire dans 15 minutes.%n%n" +
                            "Si vous n'êtes pas concerné(e), ignorez cet email.%n%n" +
                            "L'équipe %s",
                    firstName != null ? firstName : "", appName, activationLink, appName
            );

            String safeName = org.springframework.web.util.HtmlUtils.htmlEscape(firstName != null ? firstName : "");
            String safeApp  = org.springframework.web.util.HtmlUtils.htmlEscape(appName);
            String safeLink = org.springframework.web.util.HtmlUtils.htmlEscape(activationLink);

            String htmlBody = String.format(
                    "<html><body style=\"font-family:sans-serif;font-size:15px;line-height:1.6;color:#333;\">" +
                            "<p>Bonjour <strong>%s</strong>,</p>" +
                            "<p>L'administrateur de la plateforme <strong>%s</strong> vous a créé un compte.</p>" +
                            "<p>Votre identifiant de connexion : <strong>%s</strong></p>" +
                            "<p>Pour activer votre compte et définir votre mot de passe, cliquez sur le bouton ci-dessous :</p>" +
                            "<p style=\"margin:28px 0;\">" +
                            "  <a href=\"%s\" style=\"display:inline-block;padding:13px 28px;background-color:#1a56db;" +
                            "     color:#ffffff;text-decoration:none;border-radius:8px;font-weight:700;font-size:15px;\">" +
                            "    Activer mon compte" +
                            "  </a>" +
                            "</p>" +
                            "<p style=\"font-size:13px;color:#666;\">Si le bouton ne fonctionne pas, copiez cette adresse dans votre navigateur :</p>" +
                            "<p style=\"word-break:break-all;font-size:12px;color:#444;\">%s</p>" +
                            "<p style=\"margin-top:20px;\">Ce lien est valable <strong>15 minutes</strong> à partir de sa réception.</p>" +
                            "<p>Si vous n'êtes pas concerné(e) par ce message, ignorez cet email.</p>" +
                            "<p>Cordialement,<br>L'équipe <strong>%s</strong></p>" +
                            "</body></html>",
                    safeName,
                    safeApp,
                    HtmlUtils.htmlEscape(email),
                    safeLink,
                    safeLink,
                    safeApp
            );

            helper.setText(textBody, htmlBody);
            mailSender.send(message);
            log.info("Lien d'activation utilisateur envoyé avec succès à : {}", email);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du lien d'activation utilisateur à {} : {}", email, e.getMessage(), e);
            throw new RuntimeException("Impossible d'envoyer l'email d'activation", e);
        }
    }

    // =========================================================================
    // MOT DE PASSE
    // =========================================================================
    @Override
    public void sendPasswordResetCode(String email, String code, String firstName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("Réinitialisation de mot de passe - " + appName);

            String body = String.format(
                "Bonjour %s,%n%nVous avez demandé la réinitialisation de votre mot de passe pour %s.%n%nVotre code de réinitialisation est : %s%n%nCe code expire dans 15 minutes.%n%nSi vous n'êtes pas à l'origine de cette demande, ignorez cet email.%n%nL'équipe %s",
                firstName != null ? firstName : "", appName, code, appName
            );
            
            helper.setText(body, false);
            mailSender.send(message);

            log.info("Code de réinitialisation envoyé avec succès à: {}", email);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du code de réinitialisation à {}: {}", email, e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de réinitialisation", e);
        }
    }
    // =========================================================================
    // INTERVENTIONS
    // =========================================================================

    @Override
    public void sendInterventionNotification(String email, String providerName, String title, String residenceName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("Nouvelle opportunité d'intervention - " + appName);

            String htmlBody = "<html><body style=\"font-family:sans-serif;line-height:1.6;color:#333;\">" +
                    "<div style=\"max-width:600px;margin:auto;border:1px solid #eee;padding:20px;border-radius:10px;\">" +
                    "<h2 style=\"color:#1a56db;\">Bonjour " + providerName + ",</h2>" +
                    "<p>Une nouvelle demande d'intervention vient d'être publiée dans votre secteur.</p>" +
                    "<div style=\"background-color:#f9f9f9;padding:15px;border-left:4px solid #1a56db;margin:20px 0;\">" +
                    "<strong>Titre :</strong> " + title + "<br>" +
                    "<strong>Résidence :</strong> " + residenceName +
                    "</div>" +
                    "<p>Connectez-vous à votre application mobile pour consulter les détails et envoyer votre devis.</p>" +
                    "<p>Cordialement,<br>L'équipe <strong>" + appName + "</strong></p>" +
                    "</div></body></html>";

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Notification d'intervention envoyée à : {}", email);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de la notification d'intervention à {} : {}", email, e.getMessage());
        }
    }

    @Override
    public void sendSyndicInterventionNotification(String email, String syndicName, String title, String residenceName, String ownerName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("Nouvelle demande de travaux - Partie commune - " + appName);

            String htmlBody = "<html><body style=\"font-family:sans-serif;line-height:1.6;color:#333;\">" +
                    "<div style=\"max-width:600px;margin:auto;border:1px solid #eee;padding:20px;border-radius:10px;\">" +
                    "<h2 style=\"color:#1a56db;\">Bonjour " + syndicName + ",</h2>" +
                    "<p>Un copropriétaire de la résidence <strong>" + residenceName + "</strong> a signalé un problème sur une partie commune.</p>" +
                    "<div style=\"background-color:#f9f9f9;padding:15px;border-left:4px solid #1a56db;margin:20px 0;\">" +
                    "<strong>Signalé par :</strong> " + ownerName + "<br>" +
                    "<strong>Titre :</strong> " + title + "<br>" +
                    "<strong>Résidence :</strong> " + residenceName +
                    "</div>" +
                    "<p>Connectez-vous à votre espace syndic pour consulter les détails et prendre en charge cette demande.</p>" +
                    "<p>Cordialement,<br>L'équipe <strong>" + appName + "</strong></p>" +
                    "</div></body></html>";

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Notification de travaux partie commune envoyée au syndic : {}", email);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de la notification au syndic {} : {}", email, e.getMessage());
        }
    }
}
