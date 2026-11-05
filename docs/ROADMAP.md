# Feuille de route

## Implémenté dans la fondation

- Projet Android Kotlin, Compose, Material 3 et configuration Gradle Kotlin DSL.
- Schéma Room relationnel version 3 avec migrations non destructives, import transactionnel/idempotent du catalogue, fusion des auteurs en doublon et archivage des anecdotes retirées ; l’état d’erreur d’import est présenté avec une action de nouvelle tentative.
- DataStore pour thème, consentement, préférence de renouvellement et carte du jour.
- Premier accueil, explication, collection, carte consultée et paramètres.
- Collection en grille à deux colonnes, filtrable par discipline et triable par date d’acquisition ou discipline, avec ouverture de la carte touchée dans une galerie agrandie à navigation horizontale bornée ; position de grille conservée au retour.
- Retournement 3D partagé de la carte quotidienne et de la galerie agrandie, avec extrait d’explication et accès à la lecture complète.
- Ajout automatique et idempotent à la collection après diffusion `FLAG_LOCK` réussie ou callback de récompense booster confirmé ; aucune collection sur erreur, aperçu ou publicité interrompue.
- Boosters et diffusion du fond alimentent la même table Room par transactions distinctes ; seuls les fonds réussis inscrivent un historique de livraison.
- Renderer d’affiche plein écran adaptatif aux dimensions et au thème ; illustration à l’échelle native, ajustée à la largeur et au ratio de l’écran sans découpage, textes mesurés pour les accroches et références longues, sans zone réservée à l’horloge système ; aperçu et application réutilisent le même bitmap avec recadrage plein cadre et `FLAG_LOCK` exclusivement.
- Quatorze illustrations vectorielles originales, une par catégorie, partagées entre accueil, collection, carte agrandie et fond verrouillé.
- Palettes principale/secondaire centralisées par discipline et partagées entre l’application, le renderer du fond verrouillé et l’éditeur graphique.
- Suppression de tous les numéros individuels visibles, avec conservation des identifiants techniques.
- Menu développeur uniquement en debug (7 appuis rapides sur le titre de collection), déblocage idempotent des 35 anecdotes avec sauvegarde persistante, restauration exacte, prévisualisation sans mutation et régénération/application de l’affiche sur le verrouillage uniquement ; build release sans interface ni code de mutation développeur.
- Réinitialisation de collection disponible dans le menu DEBUG, avec confirmation, effacement transactionnel des seules acquisitions Room et invalidation des attributions de booster en attente ; le catalogue et les historiques de diffusion sont conservés.
- Tests unitaires et Android couvrant sélection/consentement/collection, imports et archivage, rendu vectoriel et composition sur plusieurs résolutions ; consulter les rapports de tests pour le dernier résultat exécuté.
- Documentation produit, architecture, données et consignes agent.
- Boosters facultatifs avec UMP/AdMob Rewarded, lancement direct depuis le bouton de collection, bloc Booster intégré au défilement de la grille, paquet de révélation affiché uniquement après confirmation et sauvegarde Room transactionnelle/idempotente, IDs de démonstration en DEBUG et BETA, migration non destructive 2→3.
- Une flèche turquoise discrète indique le balayage horizontal des cartes du booster jusqu’au premier changement de page, puis disparaît.
- Catalogue élargi de 24 à 35 anecdotes et de 8 à 14 disciplines. Onze nouvelles anecdotes restent en attente de validation ; la carte sur les thermopolia conserve son ID et sa collection lors de son reclassement en archéologie.
- Tests instrumentés du repository booster : lot normal, dernières cartes disponibles, collection complète, statuts individuels, archivage préservé, callback répété et stabilité de la sélection quotidienne.
- Variante de distribution `beta` distincte (`fr.inillusio.beta`), non débogable, avec façade développeur RELEASE, annonces AdMob de démonstration, signature locale dédiée obligatoire et copie APK dans `dist/` ; procédure documentée dans `docs/BETA.md`.

- Réglage indépendant des positions du texte et des sources dans DataStore, aperçu en direct via le renderer partagé et application explicite au verrouillage.
- Statuts éditoriaux individuels dans l’éditeur et Room ; les cartes déjà collectées restent accessibles.
- Noms de stockage In Illusio, reprise non destructive de la base et des préférences, migration du travail unique WorkManager.

## En cours / à valider

- Valider le cycle WorkManager quotidien après redémarrage et la migration de schéma sur un téléphone physique. Le test d’application réel du Bitmap au verrouillage n’a pas été relancé ici, pour préserver le fond de l’émulateur.
- Valider visuellement la composition plein écran sur d’autres ratios et résolutions ; la compilation et l’installation sur l’AVD 1080 × 2400 sont vérifiées.
- Validation éditoriale et bibliographique du corpus, explicitement fourni comme proposition de travail.
- Vérification de la migration Room 1→2 sur une base réelle avant le premier déploiement physique.
- Tester UMP et l’annonce test AdMob sur appareil connecté, puis vérifier la migration 2→3 sur un appareil conservant déjà une base v2.

## Prévu

- Accessibilité et finitions adaptatives.
- Variantes graphiques supplémentaires pour les cartes, en conservant l’illustration commune à chaque catégorie.
- Finitions des transitions de navigation et des animations de sauvegarde.
- Synchronisation éventuelle d’une base distante, hors première version.

## Reporté

- Comptes, backend, analyse comportementale et achats intégrés : hors périmètre. Seules les publicités récompensées facultatives décrites par la spécification sont autorisées.
- Historique de cartes manquées accessible à l’utilisateur : exclu par la spécification.
