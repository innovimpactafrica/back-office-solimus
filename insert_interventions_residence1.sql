-- Insertion de 3 demandes d'intervention pour la résidence 1
-- Assurez-vous que la résidence 1 existe et que les IDs de syndic, spécialité, etc. sont valides

-- Demande 1: Réparation de plomberie - urgence moyenne
INSERT INTO intervention_requests 
(reference, title, description, status, initiated_by, location_type, management_mode, urgency_level, 
 syndic_id, residence_id, specialty_id, created_at, started_at, finished_at, validated_at, quote_accepted_at, 
 total_amount, deposit_amount, remaining_amount)
VALUES 
('INT-2024-1001', 'Fuite d''eau dans le couloir', 'Fuite importante dans le couloir du rez-de-chaussée, nécessite une intervention rapide.', 
 'PENDING', 'SYNDIC', 'PARTIE_COMMUNE', 'SYNDIC', 'MEDIUM', 
 1, 1, 1, NOW(), NULL, NULL, NULL, NULL, 
 0.00, 0.00, 0.00);

-- Demande 2: Réparation électrique - urgence basse
INSERT INTO intervention_requests 
(reference, title, description, status, initiated_by, location_type, management_mode, urgency_level, 
 syndic_id, residence_id, specialty_id, created_at, started_at, finished_at, validated_at, quote_accepted_at, 
 total_amount, deposit_amount, remaining_amount)
VALUES 
('INT-2024-1002', 'Éclairage défectueux parking', 'Plusieurs lampes du parking souterrain ne fonctionnent plus.', 
 'PENDING', 'SYNDIC', 'PARTIE_COMMUNE', 'SYNDIC', 'LOW', 
 1, 1, 2, NOW(), NULL, NULL, NULL, NULL, 
 0.00, 0.00, 0.00);

-- Demande 3: Réparation de serrure - urgence haute
INSERT INTO intervention_requests 
(reference, title, description, status, initiated_by, location_type, management_mode, urgency_level, 
 syndic_id, residence_id, specialty_id, created_at, started_at, finished_at, validated_at, quote_accepted_at, 
 total_amount, deposit_amount, remaining_amount)
VALUES 
('INT-2024-1003', 'Porte d''entrée bloquée', 'La porte principale de l''immeuble est bloquée, les résidents ne peuvent plus entrer.', 
 'PENDING', 'SYNDIC', 'PARTIE_COMMUNE', 'SYNDIC', 'HIGH', 
 1, 1, 3, NOW(), NULL, NULL, NULL, NULL, 
 0.00, 0.00, 0.00);

-- Note: Les IDs suivants doivent être ajustés selon vos données existantes:
-- syndic_id: ID du syndic (vérifiez dans la table users)
-- residence_id: 1 (résidence 1)
-- specialty_id: ID de la spécialité (1=Plomberie, 2=Électricité, 3=Serrurerie, etc.)
