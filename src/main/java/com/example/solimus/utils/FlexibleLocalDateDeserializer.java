package com.example.solimus.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Désérialiseur Jackson tolérant pour LocalDate :
 * accepte "2026-06-15" (ISO date), "2026-06-15T00:00:00[.SSS][Z|+02:00]" (ISO datetime)
 * et "15/06/2026" (format legacy français).
 * Permet au frontend d'envoyer une date ISO dans un body JSON sans casser l'existant.
 */
public class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            return parse(p.getValueAsString());
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        }
    }

    // Même règle, réutilisable hors Jackson (ex: @RequestParam texte d'un formulaire multipart).
    // Retourne null pour une valeur vide ; IllegalArgumentException si le format n'est pas reconnu.
    public static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();

        // ISO date : 2026-06-15
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        // ISO datetime sans offset : 2026-06-15T00:00:00(.000)
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        // ISO datetime avec offset / Z : 2026-06-15T00:00:00Z
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        // Format legacy : 15/06/2026
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("Format de date non reconnu (attendu : yyyy-MM-dd ou jj/mm/aaaa) : " + raw);
    }
}