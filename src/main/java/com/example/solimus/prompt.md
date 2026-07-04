# % année budget global
# Date de construction jour/mois/année
# Contact clé : fullName /Téléphone , mettre à jour la table à la base 

#  Le principe
Quand une SyndicWalletTransaction est créée, toi (ou ton service) décides du signe selon le type d'événement :

Un copropriétaire paie ses charges → l'argent rentre dans le portefeuille du syndic → tu enregistres amount = +1 500 000 (positif)
Le syndic paie un prestataire pour des travaux → l'argent sort du portefeuille → tu enregistres amount = -800 000 (négatif, avec le signe moins directement dans la valeur stockée)


# Au moment de la création d'une SyndicWithdrawalRequest, il doit y avoir une validation du type :
javaBigDecimal soldeDisponible = calculerSolde(wallet.getId(), LocalDateTime.now());

if (montantDemande.compareTo(soldeDisponible) > 0) {
throw new InsufficientFundsException("Solde insuffisant pour ce retrait");
}