# Feuille de route

## Implémenté dans la fondation

- Projet Android Kotlin, Compose, Material 3 et configuration Gradle Kotlin DSL.
- Schéma Room relationnel version 2 avec migration non destructive, import transactionnel/idempotent du catalogue, fusion des auteurs en doublon et archivage des anecdotes retirées ; l’état d’erreur d’import est présenté avec une action de nouvelle tentative.
- DataStore pour thème, consentement, préférence de renouvellement et carte du jour.
- Premier accueil, explication, collection, carte consultée et paramètres.
- Règle de collection conditionnée à l’historique d’une diffusion réussie.
- Renderer d’affiche plein écran adaptatif aux dimensions et au thème ; illustration dominante dans la moitié supérieure, textes mesurés pour les accroches et références longues, sans zone réservée à l’horloge système ; aperçu et application réutilisent le même bitmap avec recadrage plein cadre et `FLAG_LOCK` exclusivement.
- Huit illustrations vectorielles originales, une par catégorie, partagées entre accueil, collection, carte agrandie et fond verrouillé.
- Suppression de tous les numéros individuels visibles, avec conservation des identifiants techniques.
- Menu développeur uniquement en debug (7 appuis rapides sur le titre de collection), déblocage idempotent des 16 anecdotes avec sauvegarde persistante, restauration exacte, prévisualisation sans mutation et régénération/application de l’affiche sur le verrouillage uniquement ; build release sans interface ni code de mutation développeur.
- 8 tests unitaires et 8 tests Android ciblés exécutés : sélection/consentement/collection, idempotence des imports, archivage, JSON invalide, proportions d’illustration, décalage et textes courts/longs dans les deux thèmes à 1080 × 2400.
- Documentation produit, architecture, données et consignes agent.

## En cours / à valider

- Réglage de position verticale des textes du fond dans DataStore, aperçu en direct via le renderer partagé et application explicite au verrouillage.

- Valider le cycle WorkManager quotidien après redémarrage et la migration de schéma sur un téléphone physique. Le test d’application réel du Bitmap au verrouillage n’a pas été relancé ici, pour préserver le fond de l’émulateur.
- Valider visuellement la composition plein écran sur d’autres ratios et résolutions ; la compilation et l’installation sur l’AVD 1080 × 2400 sont vérifiées.
- Validation éditoriale et bibliographique du corpus, explicitement fourni comme proposition de travail.
- Vérification de la migration Room 1→2 sur une base réelle avant le premier déploiement physique.

## Prévu

- Liens sources réellement ouvrables, accessibilité et finitions adaptatives.
- Variantes graphiques supplémentaires pour les cartes, en conservant l’illustration commune à chaque catégorie.
- Animations de sauvegarde, agrandissement et retournement de carte.
- Variantes graphiques de cartes, tout en gardant l’identité commune des disciplines.
- Synchronisation éventuelle d’une base distante, hors première version.
- Éventuel système de boosters, seulement si le produit évolue dans ce sens.

## Reporté

- Comptes, backend, publicité, analyse comportementale et achats intégrés : hors périmètre.
- Historique de cartes manquées accessible à l’utilisateur : exclu par la spécification.
