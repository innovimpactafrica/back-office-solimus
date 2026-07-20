-- Ajouter accountNumber et reason à SyndicWithdrawalRequest
-- accountNumber : RIB ou numéro de téléphone pour le retrait
-- reason : motif du retrait

ALTER TABLE syndic_withdrawal_requests ADD COLUMN account_number VARCHAR(255);
ALTER TABLE syndic_withdrawal_requests ADD COLUMN reason VARCHAR(500);
