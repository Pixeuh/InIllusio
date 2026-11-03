# Spécification produit — In Illusio

## Concept et principes

In Illusio est une application Android en français qui diffuse une anecdote de sciences humaines par jour sous forme d’affiche de fond d’écran de verrouillage. L’utilisateur consulte ensuite une explication sourcée ; toute application réussie au verrouillage ajoute automatiquement cette carte à sa collection. Le ton est rigoureux, accessible et éditorial. L’app fonctionne hors ligne, sans serveur, compte, publicité, suivi comportemental ni achat intégré.

Les états d’une carte sont distincts : entrée du catalogue, sélection du jour, diffusion réellement réussie avec `WallpaperManager.FLAG_LOCK`, puis ajout automatique à la collection. Une carte n’est jamais collectionnée avant une diffusion réussie. Un aperçu ne constitue pas une diffusion. Une erreur ne collectionne rien. Android ne permet pas de savoir si l’utilisateur a réellement regardé son écran de verrouillage.

Les identifiants d’anecdotes restent techniques. Aucun numéro individuel de carte ne doit être rendu dans l’accueil, les fiches, la collection, la carte agrandie ou le fond d’écran. Les années des références et le nombre de cartes dans la collection restent autorisés.

La sélection quotidienne reste stable durant la journée locale. La rotation évite les cartes déjà diffusées jusqu’à épuisement, puis peut recommencer un cycle. L’historique technique des diffusions n’est pas exposé. Une carte oubliée disparaît de l’accueil et n’est pas récupérable depuis un historique utilisateur ; elle pourra revenir dans un cycle ultérieur.

## Identité visuelle

Direction : revue scientifique contemporaine, minimaliste et éditoriale. Fond clair chaud, texte noir bleuté, corail `#FF6347` et turquoise `#00BFAE` pour les éléments généraux de l’application. En sombre : fond `#0B141B`, texte `#F7F3E8`, mêmes accents généraux. Chaque discipline dispose en plus d’une paire de couleurs dédiée, définie une seule fois dans `data/discipline-palettes.css` et utilisée par les cartes et illustrations. Les éléments neutres et le texte principal restent adaptés au thème. Préférer une typographie géométrique proche de Sora, des catégories en petites capitales espacées, des textes adaptatifs et compacts. Une illustration vectorielle éditoriale est associée à chacune des huit catégories et réutilisée par ses anecdotes. Les compositions sont construites en Canvas natif avec trames, silhouettes, gravures et formes géométriques.

## Écrans et comportements attendus

- **Accueil** : carte du jour recto/verso avec retournement 3D ; le verso montre un extrait de l’explication et son bouton ouvre l’écran complet, sans bouton d’explication séparé sous la carte. La discipline, l’illustration associée à la catégorie et l’accroche restent au recto ; aucune référence bibliographique ni aucun lien ; aucun numéro individuel de carte ; accès discret à la collection. L’application réussie au fond verrouillé ajoute automatiquement la carte. Pas de barre inférieure.
- **Explication** : retour, discipline, titre, développement et références bibliographiques complètes (auteur, titre, année lorsqu’elle est disponible), sans lien Internet.
- **Collection unique « Sciences humaines »** : grille à deux colonnes des cartes dont l’application au verrouillage a réussi ; la discipline reste visible. Toucher une carte ouvre cette carte dans une vue agrandie avec navigation horizontale bornée et compteur de position. Le retour restaure la position de défilement de la grille.
- **Carte agrandie** : carte recto/verso avec retournement 3D ; un balayage horizontal change de carte sans déclencher le retournement. Le verso présente un extrait de l’explication et ouvre l’écran complet. L’explication complète conserve ses références.
- **Paramètres** : thème système/clair/sombre, préférence de renouvellement du verrouillage, prévisualisation, application manuelle et informations de version.
- **Première ouverture** : expliquer la carte quotidienne, le renouvellement et la collection. Le consentement au renouvellement est explicite. Une fois activé, le renouvellement peut appliquer automatiquement l’affiche au verrouillage ; le fond d’accueil n’est jamais modifié.

## Fond d’écran

Un renderer dédié produit une affiche adaptée aux dimensions du téléphone : illustration large dans la partie supérieure, conservant son rapport natif sans découpage horizontal, catégorie sous l’illustration, anecdote au centre ou dans la moitié inférieure, puis référence bibliographique courte (auteur · publication · année) près du bas, sans URL. La hauteur occupée par l’illustration dépend du ratio de l’écran ; les textes longs peuvent réduire sa taille. Les thèmes clair et sombre adaptent le fond et les couleurs. L’affiche occupe toute la surface ; l’horloge et les notifications Android peuvent se superposer à l’illustration. Aucun numéro individuel de carte n’est affiché. Ne jamais dessiner l’horloge ou ajouter un logo. L’application native doit employer exclusivement `WallpaperManager.FLAG_LOCK` et ne jamais toucher au fond d’accueil. L’aperçu réutilise le même bitmap et ne débloque pas la carte. Le renouvellement WorkManager est facultatif, persistant et idempotent par date locale ; Android peut retarder l’exécution. L’ouverture de l’application vérifie aussi la date actuelle.

## Données éditoriales

Le catalogue source est `data/faits_sciences_humaines_v1_16.json`, en français, avec 24 anecdotes, dont dix en sociologie. Les sources conservent les informations bibliographiques disponibles (auteur, titre et année) sans champ URL. Il est fourni comme corpus de travail à vérifier avant publication. Ne pas inventer d’anecdotes ou de références pour compléter ce corpus. S’il manque, conserver l’importeur et signaler précisément l’absence.

Chaque anecdote porte son propre champ `statut_editorial`, limité à `a_valider` ou `validee`. Seules les anecdotes `validee` sont éligibles à la sélection et à la diffusion quotidienne. Les anecdotes en attente restent dans le catalogue et les cartes déjà collectionnées restent visibles. Un statut individuel manquant lors d’un import est traité comme `a_valider`. Aucun statut éditorial global n’est utilisé.

## Contraintes produit

Kotlin, Compose, Material 3, Room, DataStore, Navigation Compose, Coroutines/Flow, ViewModel, WorkManager et Gradle Kotlin DSL. Architecture lisible par responsabilités, sans module superflu. Pas de synchronisation distante dans cette première étape ; prévoir une séparation entre stockage, repository, logique métier et UI. Toute évolution doit maintenir le verrouillage exclusif, le consentement, la collection volontaire et le fonctionnement hors ligne.
