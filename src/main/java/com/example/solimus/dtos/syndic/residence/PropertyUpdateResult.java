package com.example.solimus.dtos.syndic.residence;

// Transport interne service → controller uniquement — jamais sérialisé tel quel en JSON.
// Le controller extrait "property" comme corps de réponse (inchangé) et "areaWarning" comme header
// HTTP (ex: X-Warning), pour ne pas polluer le DTO public avec un champ presque toujours null.
public record PropertyUpdateResult(PropertyDTO property, String areaWarning) {
}
