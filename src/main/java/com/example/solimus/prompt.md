Sur l'écran Résidences du dashboard syndic, on doit retourner deux types de données : un bandeau de statistiques globales (toutes les résidences du syndic connecté confondues), et une liste paginée de cards, une par résidence, avec ses propres chiffres. Les deux sont indépendants : filtrer ou chercher dans la liste ne doit jamais faire bouger les chiffres du bandeau.
Partie 1 — Le DTO de statistiques globales (ResidenceDashboardStatsDTO)
Sur cette partie, on veut retourner le DTO de KPI global du syndic connecté, avec les champs suivants :
Le champ totalResidences provient d'un simple comptage des résidences appartenant au syndic connecté. Il permet d'afficher le nombre total de copropriétés que ce syndic gère.
Le champ totalAppartements provient d'un comptage des lots (Property) rattachés à toutes les résidences de ce syndic. On ne doit pas se fier au champ lotsCount stocké sur Residence, parce qu'il peut être désynchronisé — on recompte directement depuis Property à chaque fois, pour être sûr que le chiffre est exact.
Le champ tresorerieGlobale provient de la somme de tous les montants réellement encaissés (le champ amountPaid sur ChargeCallItem), en remontant la chaîne ChargeCallItem vers ChargeCall vers Budget vers Residence, filtrée sur le syndic connecté. Ce chiffre représente l'argent qui est vraiment rentré, pas ce qui est théoriquement dû. S'il n'y a encore aucun paiement, il faut renvoyer zéro plutôt que null.
Le champ residencesAvecImpayes provient d'un comptage du nombre de résidences distinctes qui ont au moins un ChargeCallItem dont le statut n'est pas PAID, peu importe l'ancienneté du ChargeCall concerné — on ne filtre pas par date, un vieil impayé compte quand même.
Le champ pourcentageResidencesImpayees n'est pas une requête, c'est un calcul fait après coup en mémoire : le nombre de résidences avec impayés divisé par le nombre total de résidences, multiplié par cent. S'il n'y a aucune résidence, il faut renvoyer zéro pour éviter une division par zéro.
Le champ travauxEnCours provient d'un comptage des demandes d'intervention (InterventionRequest) dont le statut est STARTED, toutes résidences du syndic confondues.
Le champ enAttenteDevis provient d'un comptage des demandes d'intervention dont le statut est PENDING, toutes résidences du syndic confondues.
Partie 2 — Le DTO de card résidence (ResidenceCardDTO)
Sur cette partie, on veut retourner une entrée par résidence, avec ses propres chiffres, filtrable et paginée.
Les champs id, name, city, photoUrl et healthStatus viennent directement de l'entité Residence, sans calcul.
Le champ appartementsCount provient du même principe que totalAppartements plus haut, mais restreint à cette résidence précise : on compte les Property rattachées à cette résidence.
Le champ tresorerie provient du même principe que tresorerieGlobale, mais restreint à cette résidence : la somme des amountPaid de tous les ChargeCallItem qui remontent jusqu'à cette résidence via Budget et ChargeCall.
Le champ tauxImpayes est un pourcentage exprimé en montant d'argent, pas en nombre de copropriétaires. Il se calcule en prenant la somme de tous les montants dus (amountDue) moins la somme de tous les montants payés (amountPaid) pour cette résidence, divisée par la somme des montants dus, multipliée par cent. On a choisi ce mode de calcul plutôt qu'un comptage de lignes en impayé, parce qu'un taux basé sur le nombre de copropriétaires donnerait une image trompeuse de la santé financière : un copropriétaire qui doit une somme infime pèserait autant qu'un copropriétaire qui n'a presque rien payé. Si la résidence n'a encore aucun ChargeCall généré, le montant dû total est zéro, et il faut renvoyer zéro pour éviter une division par zéro.
Le champ travauxEnCours sur la card suit la même logique que dans le bandeau global, mais restreint à cette résidence uniquement : le nombre d'InterventionRequest au statut STARTED pour cette résidence précise.
Partie 3 — Recherche, filtres et pagination sur la liste de cards
La liste de cards doit pouvoir être filtrée par une recherche texte sur le nom de la résidence (recherche partielle, insensible à la casse), par ville (correspondance exacte si un filtre est fourni), et par statut de santé (correspondance exacte si un filtre est fourni). Elle doit aussi être paginée. Dans tous les cas, peu importe les filtres appliqués, la liste ne doit jamais montrer les résidences d'un autre syndic que celui actuellement connecté — c'est une contrainte de sécurité systématique, pas un filtre optionnel.

Le champ healthStatus n'est pas lu directement depuis la base, il est calculé à la volée à chaque construction du DTO, à partir de deux éléments : le tauxImpayes de la résidence (calculé comme décrit plus haut), et la présence d'au moins une demande d'intervention urgente encore active pour cette résidence.
Une intervention compte comme "urgente et active" si son urgencyLevel vaut URGENT et que son statut n'est ni FINAL_VALIDATION ni CANCELLED.
La règle de calcul est la suivante : si le taux d'impayés dépasse 10%, ou s'il existe au moins une intervention urgente active, le statut est CRITIQUE. Sinon, si le taux d'impayés dépasse 5%, le statut est ATTENTION. Dans tous les autres cas, le statut est EXCELLENT.
Ce calcul ne modifie jamais le champ healthStatus stocké sur l'entité Residence — il sert uniquement à remplir le DTO retourné au front.


##Partie COmmune

📋 Instruction à donner à l'IA intégratrice — Détail Résidence / Vue générale
Contexte général
Quand on clique sur "Voir les détails" d'une résidence, on arrive sur un écran avec un bandeau commun à tous les onglets, puis un contenu spécifique à l'onglet sélectionné. On décrit ici le bandeau et l'onglet "Vue générale".
Le bandeau supérieur (commun à tous les onglets de cette résidence)
Il contient les informations de base de la résidence (nom, badge de statut santé, adresse courte, photo), déjà disponibles directement sur l'entité Residence — aucun calcul nécessaire pour cette partie-là.
Ensuite, il y a 5 indicateurs chiffrés propres à cette résidence :
Le total appartements provient du nombre de lots (Property) rattachés à cette résidence précise.
Le budget annuel vient directement du champ correspondant déjà présent sur l'entité Residence.
Le nombre de copropriétaires provient du nombre de propriétaires distincts parmi les lots de cette résidence — c'est-à-dire qu'on compte les utilisateurs uniques (pas les lots), puisqu'un même copropriétaire peut posséder plusieurs lots dans la résidence et ne doit être compté qu'une seule fois.
Les travaux en cours proviennent du nombre de demandes d'intervention rattachées à cette résidence dont le statut est STARTED.
Les travaux en attente de devis proviennent du nombre de demandes d'intervention rattachées à cette résidence dont le statut est PENDING.
Ces deux derniers indicateurs suivent exactement la même logique que celle déjà utilisée sur le tableau de bord global des résidences, mais restreinte à cette résidence unique au lieu de toutes les résidences du syndic.
Le contenu de l'onglet "Vue générale"
La présentation de la résidence est le champ description déjà présent sur l'entité, affiché tel quel.
L'année de construction combine deux champs déjà existants sur l'entité Residence : l'année de construction et, si elle existe, l'année de rénovation, affichées ensemble.
Le niveau de sécurité est un texte obtenu en concaténant les noms de toutes les fonctionnalités de sécurité actives rattachées à cette résidence, via la relation déjà existante entre Residence et SecurityFeature.
Les contacts clés proviennent de la liste des contacts rattachés à cette résidence, déjà disponible via l'entité ResidenceContact — chaque entrée affiche le nom, le rôle, et permet de contacter la personne par email ou téléphone selon les informations disponibles.
La localisation utilise directement la latitude, la longitude et l'adresse complète déjà stockées sur l'entité Residence, pour afficher une carte et l'adresse en texte.
Sécurité
Comme pour toutes les routes de ce module, il faut systématiquement vérifier que la résidence demandée appartient bien au syndic actuellement connecté avant de retourner la moindre donnée — ce n'est pas un filtre optionnel, c'est une vérification obligatoire à chaque appel.


